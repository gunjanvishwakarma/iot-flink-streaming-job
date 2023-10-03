package com.gunjan.alerting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.influxdb.client.write.Point;
import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
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

public class IoTAlertingJob_V5 {
    private static final String region = "us-west-2";
    private static final String inputStreamName = "ExampleInputStream";

    private static DataStream<String> createSourceFromStaticConfig(StreamExecutionEnvironment env) {
        Properties inputProperties = new Properties();
        inputProperties.setProperty(ConsumerConfigConstants.AWS_REGION, region);
        inputProperties.setProperty(ConsumerConfigConstants.STREAM_INITIAL_POSITION, "LATEST");
        return env.addSource(new FlinkKinesisConsumer<>(inputStreamName, new SimpleStringSchema(), inputProperties));
    }

    public static void main(String[] args) throws Exception {

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("group.id", "flink");

        StreamExecutionEnvironment executionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment();

        executionEnvironment
                .addSource(new FlinkKafkaConsumer<>("devicePayload", new SimpleStringSchema(), properties))
                .map((MapFunction<String, JsonObject>) devicePayload -> new JsonParser().parse(devicePayload).getAsJsonObject())
                .keyBy((KeySelector<JsonObject, String>) flinkDevicePayload -> flinkDevicePayload.get("devEUI").getAsString())
                .connect(
                        executionEnvironment
                                .addSource(new FlinkKafkaConsumer<>("jsonPath", new SimpleStringSchema(), properties))
                                .map((MapFunction<String, JsonObject>) jsonPath -> new JsonParser().parse(jsonPath).getAsJsonObject())
                                .flatMap(new FlatMapFunction<JsonObject, JsonObject>() {
                                    @Override
                                    public void flatMap(JsonObject jsonPath, Collector<JsonObject> collector) throws Exception {
                                        JsonArray devices = jsonPath.get("devices").getAsJsonArray();
                                        JsonArray alerts = jsonPath.get("alerts").getAsJsonArray();
                                        devices.forEach(jsonElement -> {
                                            JsonObject jsonObject = new JsonObject();
                                            jsonObject.addProperty("device", jsonElement.getAsString());
                                            jsonObject.add("alerts", alerts);
                                            collector.collect(jsonObject);
                                        });
                                    }
                                }).keyBy((KeySelector<JsonObject, String>) jsonObject -> jsonObject.get("device").getAsString())
                )

                .process(getKeyedCoProcessFunction())
                .map((MapFunction<JsonObject, Point>) payload -> Point.measurement("alarm")
                        .addField("name", payload.get("name").getAsString())
                        .addField("description", payload.get("description").getAsString())
                        .addField("severity", payload.get("severity").getAsString())
                        .addField("jsonPath", payload.get("jsonPath").getAsString())
                        .addField("devicePayload", payload.get("devicePayload").getAsString())).addSink(new InfluxDBSink());

        executionEnvironment.execute("IoTDataProcessing");

    }

    @NotNull
    private static KeyedCoProcessFunction<String, JsonObject, JsonObject, JsonObject> getKeyedCoProcessFunction() {
        return new KeyedCoProcessFunction<String, JsonObject, JsonObject, JsonObject>() {

            final ValueStateDescriptor<JsonObject> alertsDescriptor =
                    new ValueStateDescriptor<>(
                            "alerts",
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));
            final ValueStateDescriptor<JsonObject> widestAlertDescriptor =
                    new ValueStateDescriptor<>(
                            "widestAlert",
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));

            final ValueStateDescriptor<JsonObject> widestEventCountDescriptor =
                    new ValueStateDescriptor<>(
                            "widestEventCount",
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));
            private final MapStateDescriptor<Long, JsonObject> windowStateDescriptor =
                    new MapStateDescriptor<>(
                            "windowState",
                            BasicTypeInfo.LONG_TYPE_INFO,
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));
            private transient ValueState<JsonObject> alertList;
            private transient ValueState<JsonObject> widestAlert;
            private transient ValueState<JsonObject> widestEventCount;
            private transient MapState<Long, JsonObject> windowState;

            @Override
            public void open(Configuration config) {

                alertList = getRuntimeContext().getState(alertsDescriptor);

                widestAlert = getRuntimeContext().getState(widestAlertDescriptor);

                widestEventCount = getRuntimeContext().getState(widestEventCountDescriptor);

                windowState = getRuntimeContext().getMapState(windowStateDescriptor);
            }

            @Override
            public void processElement1(JsonObject devicePayload, Context context, Collector<JsonObject> collector) throws Exception {

                long currentEventTime = Instant.now().toEpochMilli();
                addToStateValuesSet(windowState, currentEventTime, devicePayload);

                long cleanupTime = (currentEventTime / 1000) * 1000;
                context.timerService().registerProcessingTimeTimer(cleanupTime);

                final JsonObject alerts = alertList.value();
                for (JsonElement jsonElement : alerts.get("alerts").getAsJsonArray()) {
                    JsonObject alertDetail = jsonElement.getAsJsonObject();

                    final JsonElement windowSize = alertDetail.get("windowSize");
                    JsonObject payloads = new JsonObject();
                    if (windowSize != null) {
                        Long windowStartForEvent = (currentEventTime - windowSize.getAsInt());

                        JsonArray jsonArray = new JsonArray();
                        for (Long stateEventTime : windowState.keys()) {
                            if (isStateValueInWindow(stateEventTime, windowStartForEvent, currentEventTime)) {
                                jsonArray.add(windowState.get(stateEventTime));
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

                    final Boolean isCreateAlarm = JsonPath.from(payloads.toString()).get(alertDetail.get("jsonPath").getAsString());
                    if (isCreateAlarm) {
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("name", alertDetail.get("name").getAsString());
                        jsonObject.addProperty("description", alertDetail.get("description").getAsString());
                        jsonObject.addProperty("severity", alertDetail.get("severity").getAsString());
                        jsonObject.addProperty("jsonPath", alertDetail.get("jsonPath").getAsString());
                        jsonObject.addProperty("devicePayload", devicePayload.toString());
                        collector.collect(jsonObject);
                    }
                }
            }

            @Override
            public void processElement2(JsonObject alerts, Context context, Collector<JsonObject> collector) throws Exception {
                alertList.update(alerts);
                updateWidestWindowRule(alerts);
            }

            @Override
            public void onTimer(long timestamp, OnTimerContext ctx, Collector<JsonObject> out) throws Exception {
                JsonObject widestWindowRule = widestAlert.value();

                Optional<Long> cleanupEventTimeWindow =
                        Optional.ofNullable(widestWindowRule).map(jsonObject -> jsonObject.getAsJsonObject().get("windowSize").getAsLong());
                Optional<Long> cleanupEventTimeThreshold =
                        cleanupEventTimeWindow.map(window -> timestamp - window);

                cleanupEventTimeThreshold.ifPresent(this::evictAgedElementsFromWindow);
                StreamSupport.stream(windowState.keys().spliterator(), false)
                        .sorted().forEach(new Consumer<Long>() {
                    @SneakyThrows
                    @Override
                    public void accept(Long aLong) {
                        final JsonObject jsonObject = windowState.get(aLong);
                        final LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(aLong), ZoneId.systemDefault());
                        System.out.println(localDateTime + " -> " + jsonObject.get("object"));
                    }
                });
            }

            private void evictAgedElementsFromWindow(Long threshold) {
                try {

                    final JsonObject value = widestEventCount.value();
                    final int eventCount = value.get("eventCount").getAsInt();
                    int currentWindowSize = 0;
                    if(windowState.keys().iterator().hasNext()){
                        currentWindowSize++;
                    }

                    if(currentWindowSize <= eventCount){
                        return;
                    }

                    Iterator<Long> keys = windowState.keys().iterator();
                    while (keys.hasNext() &&  currentWindowSize > eventCount) {
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

            private void updateWidestWindowRule(JsonObject rule)
                    throws Exception {
                JsonArray alerts = (JsonArray) rule.get("alerts");
                alerts.iterator().forEachRemaining(new Consumer<JsonElement>() {
                    @SneakyThrows
                    @Override
                    public void accept(JsonElement alert) {
                        final JsonElement windowSize = alert.getAsJsonObject().get("windowSize");
                        if (windowSize != null) {
                            final int currentWindowSize = windowSize.getAsInt();
                            if (widestAlert.value() == null) {
                                widestAlert.update((JsonObject) alert);
                            } else {
                                final int widestWindow = widestAlert.value().get("windowSize").getAsInt();
                                if (widestWindow < currentWindowSize) {
                                    widestAlert.update((JsonObject) alert);
                                }
                            }
                        } else {
                            final JsonElement eventCount = alert.getAsJsonObject().get("eventCount");
                            if (eventCount != null) {
                                final int currentEventCount = eventCount.getAsInt();
                                if (widestEventCount.value() == null) {
                                    widestEventCount.update((JsonObject) alert);
                                } else {
                                    final int widestWindow = widestEventCount.value().get("eventCount").getAsInt();
                                    if (widestWindow < currentEventCount) {
                                        widestEventCount.update((JsonObject) alert);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        };
    }

    private static void addToStateValuesSet(MapState<Long, JsonObject> windowState, Long currentEventTime, JsonObject devicePayload) throws Exception {
        windowState.put(currentEventTime, devicePayload);
    }
}