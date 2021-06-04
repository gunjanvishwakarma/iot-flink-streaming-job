package com.gunjan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gunjan.template.Template;
import com.influxdb.client.write.Point;
import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.util.Collector;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class IoTAlertingJob_V6 {
    public static final String WIDEST_ALERT_KEY = "WIDEST_ALERT_KEY";
    public static final String WIDEST_EVENT_COUNT_KEY = "WIDEST_EVENT_COUNT_KEY";

    public static void main(String[] args) throws Exception {

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("group.id", "flink");

        StreamExecutionEnvironment executionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment();

        final FlinkKafkaConsumer<String> devicePayloadConsumer = new FlinkKafkaConsumer<>("devicePayload", new SimpleStringSchema(), properties);
        final FlinkKafkaConsumer<String> alertConsumer = new FlinkKafkaConsumer<>("jsonPath", new SimpleStringSchema(), properties);
        final BroadcastStream<JsonObject> broadcast = executionEnvironment
                .addSource(alertConsumer)
                .map((MapFunction<String, JsonObject>) alert -> new JsonParser().parse(alert).getAsJsonObject())
                .broadcast(new MapStateDescriptor<>("AlertsBroadcastState", BasicTypeInfo.STRING_TYPE_INFO, TypeInformation.of(new TypeHint<Template>() {
                })));

        executionEnvironment
                .addSource(devicePayloadConsumer)
                .map((MapFunction<String, JsonObject>) devicePayload -> new JsonParser().parse(devicePayload).getAsJsonObject())
                .connect(broadcast)
                .process(getDyanamicKeyFunction())
                .keyBy((KeySelector<Keyed<JsonObject, String, String>, String>) stringStringStringKeyed -> stringStringStringKeyed.getKey())
                .connect(broadcast)
                .process(getDynamicAlertFunction())
                .map((MapFunction<JsonObject, Point>) payload -> Point.measurement("alarm")
                        .addField("name", payload.get("name").getAsString())
                        .addField("description", payload.get("description").getAsString())
                        .addField("severity", payload.get("severity").getAsString())
                        .addField("jsonPath", payload.get("jsonPath").getAsString())
                        .addField("devicePayload", payload.get("devicePayload").getAsString())).addSink(new InfluxDBSink());


        executionEnvironment.execute("IoTDataProcessing");

    }

    @NotNull
    private static BroadcastProcessFunction<JsonObject, JsonObject, Keyed<JsonObject, String, String>> getDyanamicKeyFunction() {
        return new BroadcastProcessFunction<JsonObject, JsonObject, Keyed<JsonObject, String, String>>() {
            private final MapStateDescriptor<String, JsonObject> alertStateDescriptor =
                    new MapStateDescriptor<>(
                            "AlertsBroadcastState",
                            BasicTypeInfo.STRING_TYPE_INFO,
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));

            @Override
            public void processElement(JsonObject devicePayload, ReadOnlyContext readOnlyContext, Collector<Keyed<JsonObject, String, String>> out) throws Exception {
                ReadOnlyBroadcastState<String, JsonObject> rulesState =
                        readOnlyContext.getBroadcastState(alertStateDescriptor);
                forkEventForEachGroupingKey(devicePayload, rulesState, out);
            }

            private void forkEventForEachGroupingKey(
                    JsonObject event,
                    ReadOnlyBroadcastState<String, JsonObject> rulesState,
                    Collector<Keyed<JsonObject, String, String>> out)
                    throws Exception {
                for (Map.Entry<String, JsonObject> entry : rulesState.immutableEntries()) {
                    final JsonObject rule = entry.getValue();
                    final JsonArray groupBy = rule.get("groupBy").getAsJsonArray();
                    List<String> list = new ArrayList<>();
                    groupBy.forEach(jsonElement -> list.add(jsonElement.getAsString()));
                    out.collect(new Keyed<>(event, KeysExtractor.getKey(list, event), rule.get("ruleId").getAsString()));
                }
            }

            @Override
            public void processBroadcastElement(JsonObject jsonPath, Context context, Collector<Keyed<JsonObject, String, String>> collector) throws Exception {
                final JsonArray alerts = jsonPath.get("alerts").getAsJsonArray();
                for (JsonElement jsonObject : alerts) {
                    final JsonObject asJsonObject = jsonObject.getAsJsonObject();
                    context.getBroadcastState(alertStateDescriptor).put(asJsonObject.get("ruleId").getAsString(), asJsonObject);
                }
            }
        };
    }

    @NotNull
    private static KeyedBroadcastProcessFunction<JsonObject, Keyed<JsonObject, String, String>, JsonObject, JsonObject> getDynamicAlertFunction() {

        return new KeyedBroadcastProcessFunction<JsonObject, Keyed<JsonObject, String, String>, JsonObject, JsonObject>() {

            private final MapStateDescriptor<String, JsonObject> alertsDescriptor =
                    new MapStateDescriptor<>(
                            "AlertsBroadcastState",
                            BasicTypeInfo.STRING_TYPE_INFO,
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));

            final MapStateDescriptor<String,JsonObject> widestAlertDescriptor =
                    new MapStateDescriptor<>(
                            "AlertsBroadcastState",
                            BasicTypeInfo.STRING_TYPE_INFO,
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));

            final MapStateDescriptor<String,JsonObject> widestEventCountDescriptor =
                    new MapStateDescriptor<>(
                            "AlertsBroadcastState",
                            BasicTypeInfo.STRING_TYPE_INFO,
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));
            private final MapStateDescriptor<Long, JsonObject> windowStateDescriptor =
                    new MapStateDescriptor<Long,JsonObject>(
                            "windowState",
                            BasicTypeInfo.LONG_TYPE_INFO,
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));

            private transient MapState<Long, JsonObject> windowState;

            @Override
            public void open(Configuration parameters) {
                windowState = getRuntimeContext().getMapState(windowStateDescriptor);
            }


            @Override
            public void processElement(Keyed<JsonObject, String, String> jsonObjectStringStringKeyed, ReadOnlyContext readOnlyContext, Collector<JsonObject> collector) throws Exception {
                long currentEventTime = Instant.now().toEpochMilli();
                addToStateValuesSet(windowState, currentEventTime, jsonObjectStringStringKeyed.getWrapped());

                long cleanupTime = (currentEventTime / 1000) * 1000;
                readOnlyContext.timerService().registerProcessingTimeTimer(cleanupTime);

                JsonObject alertDetail = readOnlyContext.getBroadcastState(alertsDescriptor).get(jsonObjectStringStringKeyed.getId());

                final JsonElement windowSize = alertDetail.get("windowSize");

                List<String> devicesDevEui = new ArrayList<>();
                final JsonArray devices = alertDetail.get("devices").getAsJsonArray();
                devices.forEach(jsonElement -> devicesDevEui.add(jsonElement.getAsString()));

                JsonObject payloads = new JsonObject();
                if (windowSize != null) {
                    Long windowStartForEvent = (currentEventTime - windowSize.getAsInt());

                    JsonArray jsonArray = new JsonArray();
                    for (Long stateEventTime : windowState.keys()) {
                        final JsonObject element = windowState.get(stateEventTime);
                        if (isStateValueInWindow(stateEventTime, windowStartForEvent, currentEventTime) && devicesDevEui.contains(element.get("devEUI").getAsString())) {
                            jsonArray.add(element);
                        }
                    }
                    payloads.add("payloads", jsonArray);
                } else {
                    final JsonElement eventCount = alertDetail.get("eventCount");
                    if (eventCount != null) {
                        JsonArray jsonArray = new JsonArray();

                        int count = eventCount.getAsInt();
                        final List<Long> sorted = StreamSupport.stream(windowState.keys().spliterator(), false)
                                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());

                        for (Long aLong : sorted) {
                            jsonArray.add(windowState.get(aLong));
                            count--;
                            if (count == 0) {
                                break;
                            }
                        }
                        payloads.add("payloads", jsonArray);
                    }
                }

                final Boolean isCreateAlarm;
                try {
                    isCreateAlarm = JsonPath.from(payloads.toString()).get(alertDetail.get("jsonPath").getAsString());
                    if (isCreateAlarm) {
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("name", alertDetail.get("name").getAsString());
                        jsonObject.addProperty("description", alertDetail.get("description").getAsString());
                        jsonObject.addProperty("severity", alertDetail.get("severity").getAsString());
                        jsonObject.addProperty("jsonPath", alertDetail.get("jsonPath").getAsString());
                        jsonObject.addProperty("devicePayload", payloads.toString());
                        System.out.println(jsonObject);
                        collector.collect(jsonObject);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void processBroadcastElement(JsonObject jsonObject, Context context, Collector<JsonObject> collector) throws Exception {
                final JsonArray alerts1 = jsonObject.get("alerts").getAsJsonArray();
                for (JsonElement jsonElement : alerts1) {
                    final JsonObject asJsonObject = jsonElement.getAsJsonObject();
                    context.getBroadcastState(alertsDescriptor).put(asJsonObject.get("ruleId").getAsString(), asJsonObject);
                }

                updateWidestWindowRule(jsonObject,  context);
            }

            @Override
            public void onTimer(long timestamp, OnTimerContext ctx, Collector<JsonObject> out) throws Exception {
                JsonObject widestWindowRule = ctx.getBroadcastState(widestAlertDescriptor).get(WIDEST_ALERT_KEY);

                Optional<Long> cleanupEventTimeWindow =
                        Optional.ofNullable(widestWindowRule).map(jsonObject -> jsonObject.getAsJsonObject().get("windowSize").getAsLong());
                Optional<Long> cleanupEventTimeThreshold =
                        cleanupEventTimeWindow.map(window -> timestamp - window);

                cleanupEventTimeThreshold.ifPresent(threshold -> evictAgedElementsFromWindow(threshold,ctx));
                StreamSupport.stream(windowState.keys().spliterator(), false)
                        .sorted().forEach(new Consumer<Long>() {
                    @SneakyThrows
                    @Override
                    public void accept(Long aLong) {
                        final JsonObject jsonObject = windowState.get(aLong);
                        final LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(aLong), ZoneId.systemDefault());
                    }
                });
            }

            private void evictAgedElementsFromWindow(Long threshold, OnTimerContext ctx) {
                try {

                    final JsonObject value = ctx.getBroadcastState(widestEventCountDescriptor).get(WIDEST_EVENT_COUNT_KEY);
                    final int eventCount = value.get("eventCount").getAsInt();
                    int currentWindowSize = 0;
                    if (windowState.keys().iterator().hasNext()) {
                        currentWindowSize++;
                    }

                    if (currentWindowSize <= eventCount) {
                        return;
                    }

                    Iterator<Long> keys = windowState.keys().iterator();
                    while (keys.hasNext() && currentWindowSize > eventCount) {
                        Long stateEventTime = keys.next();
                        if (stateEventTime < threshold) {
                            keys.remove();
                            currentWindowSize--;
                        }
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            private boolean isStateValueInWindow(
                    Long stateEventTime, Long windowStartForEvent, long currentEventTime) {
                return stateEventTime >= windowStartForEvent && stateEventTime <= currentEventTime;
            }

            private void updateWidestWindowRule(JsonObject rule, Context context)
                    throws Exception {

                //context.getBroadcastState(alertsDescriptor).put(asJsonObject.get("ruleId").getAsString(), asJsonObject);
                JsonArray alerts = (JsonArray) rule.get("alerts");
                alerts.iterator().forEachRemaining(new Consumer<JsonElement>() {
                    @SneakyThrows
                    @Override
                    public void accept(JsonElement alert) {
                        final JsonElement windowSize = alert.getAsJsonObject().get("windowSize");
                        if (windowSize != null) {
                            final int currentWindowSize = windowSize.getAsInt();
                            final JsonObject jsonObject = context.getBroadcastState(widestAlertDescriptor).get(WIDEST_ALERT_KEY);
                            if (jsonObject == null) {
                                context.getBroadcastState(widestAlertDescriptor).put(WIDEST_ALERT_KEY, jsonObject);
                            } else {
                                final int widestWindow = context.getBroadcastState(widestAlertDescriptor).get(WIDEST_ALERT_KEY).getAsInt();
                                if (widestWindow < currentWindowSize) {
                                    context.getBroadcastState(widestAlertDescriptor).put(WIDEST_ALERT_KEY, jsonObject);
                                }
                            }
                        } else {
                            final JsonElement eventCount = alert.getAsJsonObject().get("eventCount");
                            if (eventCount != null) {
                                final int currentEventCount = eventCount.getAsInt();
                                final JsonObject jsonObject = context.getBroadcastState(widestEventCountDescriptor).get(WIDEST_EVENT_COUNT_KEY);
                                if (jsonObject == null) {
                                    context.getBroadcastState(widestEventCountDescriptor).put(WIDEST_EVENT_COUNT_KEY, jsonObject);
                                } else {
                                    final int widestWindow = context.getBroadcastState(widestEventCountDescriptor).get(WIDEST_EVENT_COUNT_KEY).getAsInt();
                                    if (widestWindow < currentEventCount) {
                                        context.getBroadcastState(widestEventCountDescriptor).put(WIDEST_EVENT_COUNT_KEY, jsonObject);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        };
    }

    private static void addToStateValuesSet(MapState<Long, JsonObject> mapState, Long currentEventTime, JsonObject devicePayload) throws Exception {
        mapState.put(currentEventTime, devicePayload);
    }
}