package com.gunjan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.influxdb.client.write.Point;
import io.restassured.path.json.JsonPath;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class IoTAlertingJob_V7 {
    public static Integer WIDEST_ALERT_KEY = Integer.MIN_VALUE;
    public static Integer WIDEST_EVENT_COUNT_KEY = Integer.MIN_VALUE + 1;

    public static void main(String[] args) throws Exception {

        ObjectMapper objectMapper = new ObjectMapper();

        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("group.id", "flink");


        StreamExecutionEnvironment executionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment();

        FlinkKafkaConsumer<String> eventConsumer = getEventConsumer(properties);

        FlinkKafkaConsumer<String> ruleConsumer = getRuleConsumer(properties);

        BroadcastStream<Rule> ruleBroadcastStream = executionEnvironment
                .addSource(ruleConsumer)
                .map((MapFunction<String, Rule>) rule -> objectMapper.readValue(rule, Rule.class))
                .broadcast(Descriptors.rulesDescriptor);

        executionEnvironment
                .addSource(eventConsumer)
                .map(mapToEvent())
                .connect(ruleBroadcastStream)
                .process(getDynamicKeyFunction())
                .keyBy(keyed -> keyed.getKey(), TypeInformation.of(String.class))
                .connect(ruleBroadcastStream)
                .process(getDynamicAlertFunction())
                .map(mapToInfluxPoint())
                .addSink(new InfluxDBSink());

        executionEnvironment.execute("IoTDataProcessing");

    }


    private static FlinkKafkaConsumer<String> getRuleConsumer(Properties properties) {
        return new FlinkKafkaConsumer<>("jsonPath", new SimpleStringSchema(), properties);
    }


    private static FlinkKafkaConsumer<String> getEventConsumer(Properties properties) {
        return new FlinkKafkaConsumer<>("devicePayload", new SimpleStringSchema(), properties);
    }


    private static MapFunction<String, Event> mapToEvent() {
        return eventStr -> {
            Event event = new Event();
            event.setPayload(new JsonParser().parse(eventStr).getAsJsonObject());
            return event;
        };
    }


    private static MapFunction<JsonObject, Point> mapToInfluxPoint() {
        return payload -> Point.measurement("alarm")
                .addField("name", payload.get("name").getAsString())
                //.addField("description", payload.get("description").getAsString())
                //.addField("severity", payload.get("severity").getAsString())
                .addField("jsonPath", payload.get("jsonPath").getAsString());
        //.addField("devicePayload", payload.get("devicePayload").getAsString())
        //.addField("windowSize", payload.get("windowSize").getAsString());
    }

    private static BroadcastProcessFunction<Event, Rule, Keyed<Event, String, Integer>> getDynamicKeyFunction() {
        return new BroadcastProcessFunction<Event, Rule, Keyed<Event, String, Integer>>() {

            @Override
            public void processElement(Event event, ReadOnlyContext readOnlyContext, Collector<Keyed<Event, String, Integer>> out) throws Exception {
                forkEventPerGroupingKey(event, readOnlyContext.getBroadcastState(Descriptors.rulesDescriptor), out);
            }

            @Override
            public void processBroadcastElement(Rule rule, Context context, Collector<Keyed<Event, String, Integer>> collector) throws Exception {
                context.getBroadcastState(Descriptors.rulesDescriptor).put(rule.getRuleId(), rule);
            }

            private void forkEventPerGroupingKey(
                    Event event,
                    ReadOnlyBroadcastState<Integer, Rule> rulesState,
                    Collector<Keyed<Event, String, Integer>> out)
                    throws Exception {
                HashMap<String, Keyed> keys = new HashMap<>();
                for (Map.Entry<Integer, Rule> ruleEntry : rulesState.immutableEntries()) {
                    Rule rule = ruleEntry.getValue();
                    String key = KeysExtractor.getKey(rule.getGroupingKeyJsonPaths(), event);
                    Keyed keyed = keys.get(key);
                    if (isRuleApplicableForCurrentEvent(event, rule)) {
                        if (keyed == null) {
                            List<Integer> ruleIds = new ArrayList<>();
                            ruleIds.add(rule.getRuleId());
                            keys.put(key, new Keyed<>(event, key, ruleIds));
                        } else {
                            keyed.getRuleIds().add(rule.getRuleId());
                        }
                    }
                }

                keys.values().forEach(keyed -> {
                    out.collect(keyed);
                });
            }

            private boolean isRuleApplicableForCurrentEvent(Event event, Rule rule) {
                List<String> deviceList = rule.getDevices();
                String deviceId = JsonPath.from(event.getPayload().toString()).get(rule.getDeviceIdJsonPath());
                final boolean contains = deviceList.contains(deviceId);
                return contains;
            }
        };
    }


    private static KeyedBroadcastProcessFunction<Event, Keyed<Event, String, Integer>, Rule, JsonObject> getDynamicAlertFunction() {

        return new KeyedBroadcastProcessFunction<Event, Keyed<Event, String, Integer>, Rule, JsonObject>() {

            private transient MapState<Long, Event> windowState;

            @Override
            public void open(Configuration parameters) {
                windowState = getRuntimeContext().getMapState(Descriptors.windowStateDescriptor);
            }

            @Override
            public void processElement(Keyed<Event, String, Integer> keyed, ReadOnlyContext ctx, Collector<JsonObject> collector) throws Exception {
                long processingTime = Instant.now().toEpochMilli();

                windowState.put(processingTime, keyed.getWrapped());

                long cleanupTime = (processingTime / 1000) * 1000;

                ctx.timerService().registerProcessingTimeTimer(cleanupTime);

                keyed.getRuleIds().stream().forEach(ruleId -> evaluateRule(ctx, processingTime, ruleId).ifPresent(jsonObject -> collector.collect(jsonObject)));
            }

            private Optional<JsonObject> evaluateRule(ReadOnlyContext ctx, long processingTime, Integer ruleId) {
                try {
                    Rule rule = ctx.getBroadcastState(Descriptors.rulesDescriptor).get(ruleId);

                    Long windowSize = rule.getWindowSize();

                    JsonObject payloads = new JsonObject();
                    if (windowSize != null) {
                        payloads.add("payloads", getEventsBasedOnWindowSize(processingTime, windowSize));
                    } else {
                        Long eventCount = rule.getEventCount();
                        if (eventCount != null) {
                            payloads.add("payloads", getEventsBasedOnEventCount(eventCount));
                        }
                    }
                    Boolean isRuleSatisfied = JsonPath.from(payloads.toString()).get(rule.getRuleConditionJsonPath());
                    if (isRuleSatisfied) {
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("name", rule.getName());
                        jsonObject.addProperty("description", rule.getDescription());
                        jsonObject.addProperty("severity", rule.getSeverity());
                        jsonObject.addProperty("jsonPath", rule.getRuleConditionJsonPath());
                        jsonObject.addProperty("devicePayload", payloads.toString());
                        return Optional.of(jsonObject);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return Optional.empty();
            }


            private JsonArray getEventsBasedOnEventCount(Long eventCount) throws Exception {
                JsonArray jsonArray = new JsonArray();
                List<Long> sorted = StreamSupport.stream(windowState.keys().spliterator(), false)
                        .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                for (Long aLong : sorted) {
                    jsonArray.add(windowState.get(aLong).getPayload());
                    eventCount--;
                    if (eventCount == 0) {
                        break;
                    }
                }
                return jsonArray;
            }


            private JsonArray getEventsBasedOnWindowSize(long currentEventTime, Long windowSize) throws Exception {
                JsonArray jsonArray = new JsonArray();
                Long windowStartForEvent = (currentEventTime - windowSize);
                for (Long stateEventTime : windowState.keys()) {
                    Event element = windowState.get(stateEventTime);
                    if (isStateValueInWindow(stateEventTime, windowStartForEvent, currentEventTime)) {
                        jsonArray.add(element.getPayload());
                    }
                }
                return jsonArray;
            }

            @Override
            public void processBroadcastElement(Rule rule, Context context, Collector<JsonObject> collector) throws Exception {
                context.getBroadcastState(Descriptors.rulesDescriptor).put(rule.getRuleId(), rule);
                updateWidestWindowRule(rule, context);
            }

            @Override
            public void onTimer(long timestamp, OnTimerContext ctx, Collector<JsonObject> out) throws Exception {
                Rule widestWindowRule = ctx.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_ALERT_KEY);

                Optional<Long> cleanupEventTimeWindow =
                        Optional.ofNullable(widestWindowRule).map(jsonObject -> widestWindowRule.getWindowSize());
                Optional<Long> cleanupEventTimeThreshold =
                        cleanupEventTimeWindow.map(window -> timestamp - window);

                cleanupEventTimeThreshold.ifPresent(threshold -> evictAgedElementsFromWindow(threshold, ctx));
            }

            private void evictAgedElementsFromWindow(Long threshold, OnTimerContext ctx) {
                try {

                    Rule value = ctx.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_EVENT_COUNT_KEY);
                    Long eventCount = value.getEventCount();
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

            private void updateWidestWindowRule(Rule rule, Context context)
                    throws Exception {

                Long windowSize = rule.getWindowSize();
                if (windowSize != null) {
                    Long currentWindowSize = windowSize;
                    Rule jsonObject = context.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_ALERT_KEY);
                    if (jsonObject == null) {
                        context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_ALERT_KEY, jsonObject);
                    } else {
                        Long widestWindow = context.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_ALERT_KEY).getWindowSize();
                        if (widestWindow < currentWindowSize) {
                            context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_ALERT_KEY, jsonObject);
                        }
                    }
                } else {
                    Long eventCount = rule.getEventCount();
                    if (eventCount != null) {
                        Long currentEventCount = eventCount;
                        Rule jsonObject = context.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_EVENT_COUNT_KEY);
                        if (jsonObject == null) {
                            context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_EVENT_COUNT_KEY, jsonObject);
                        } else {
                            int widestWindow = Math.toIntExact(context.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_EVENT_COUNT_KEY).getEventCount());
                            if (widestWindow < currentEventCount) {
                                context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_EVENT_COUNT_KEY, jsonObject);
                            }
                        }
                    }
                }
            }
        };
    }

}