package com.gunjan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.influxdb.client.write.Point;
import io.restassured.path.json.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
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
                .map(mapToRule(objectMapper))
                .broadcast(Descriptors.rulesDescriptor);

        executionEnvironment
                .addSource(eventConsumer)
                .map(mapToEvent())
                .connect(ruleBroadcastStream)
                .process(getDynamicKeyFunction())
                .keyBy(Keyed::getKey, TypeInformation.of(String.class))
                .connect(ruleBroadcastStream)
                .process(getDynamicAlertFunction())
                .map(mapToInfluxPoint())
                .addSink(new InfluxDBSink());

        executionEnvironment.execute("IoTDataProcessing");

    }

    @NotNull
    private static MapFunction<String, Rule> mapToRule(ObjectMapper objectMapper) {
        return rule -> objectMapper.readValue(rule, Rule.class);
    }


    private static FlinkKafkaConsumer<String> getRuleConsumer(Properties properties) {
        return new FlinkKafkaConsumer<>("rules", new SimpleStringSchema(), properties);
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
                .addField("ruleId", payload.get("ruleId").getAsString())
                //.addField("ruleConditionJsonPath", payload.get("ruleConditionJsonPath").getAsString())
                .addField("detail", payload.get("detail").getAsString());
    }

    private static BroadcastProcessFunction<Event, Rule, Keyed<Event, String, Integer>> getDynamicKeyFunction() {
        return new BroadcastProcessFunction<Event, Rule, Keyed<Event, String, Integer>>() {

            @Override
            public void processElement(Event event, ReadOnlyContext readOnlyContext, Collector<Keyed<Event, String, Integer>> out) throws Exception {
                forkEventPerGroupingKey(event, readOnlyContext.getBroadcastState(Descriptors.rulesDescriptor), out);
            }

            @Override
            public void processBroadcastElement(Rule rule, Context context, Collector<Keyed<Event, String, Integer>> collector) throws Exception {
                System.out.println(rule);
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
                if (rule.getDevices() == null || rule.getDevices().size() == 0) return true;
                List<String> deviceList = rule.getDevices();

                String deviceId = JsonPath.from(event.getPayload().toString()).get(rule.getDeviceIdJsonPath());
                return deviceList.contains(deviceId);
            }
        };
    }


    private static KeyedBroadcastProcessFunction<Event, Keyed<Event, String, Integer>, Rule, JsonObject> getDynamicAlertFunction() {

        return new KeyedBroadcastProcessFunction<Event, Keyed<Event, String, Integer>, Rule, JsonObject>() {

            private transient MapState<Long, Event> windowState;

            private transient ValueState<Long> lastTimer;

            private transient ValueState<Event> lastEvent;

            private transient ValueState<Rule> lastAlert;

            @Override
            public void open(Configuration parameters) {
                windowState = getRuntimeContext().getMapState(Descriptors.windowStateDescriptor);

                lastTimer = getRuntimeContext().getState(Descriptors.lastTimerDescriptor);

                lastEvent = getRuntimeContext().getState(Descriptors.lastFlinkDevicePayloadDescriptor);

                lastAlert = getRuntimeContext().getState(Descriptors.lastAlertDescriptor);
            }

            @Override
            public void processElement(Keyed<Event, String, Integer> keyed, ReadOnlyContext ctx, Collector<JsonObject> collector) throws Exception {
                long processingTime = Instant.now().toEpochMilli();

                windowState.put(processingTime, keyed.getWrapped());

                long cleanupTime = (processingTime / 1000) * 1000;

                ctx.timerService().registerProcessingTimeTimer(cleanupTime);

                keyed.getRuleIds().stream().map(mapToRule(ctx)).filter(rule -> rule.getRuleType().equals("condition"))
                        .forEach(ruleId -> evaluateRule(ctx, processingTime, ruleId).ifPresent(jsonObject -> collector.collect(jsonObject)));

                startUnreachableTimer(keyed, ctx);
            }

            private void startUnreachableTimer(Keyed<Event, String, Integer> keyed, ReadOnlyContext ctx) throws Exception {
                for (Integer ruleId : keyed.getRuleIds()) {
                    Rule rule = ctx.getBroadcastState(Descriptors.rulesDescriptor).get(ruleId);
                    if (rule.getRuleType().equals("unreachable")) {
                        long currentTime = ctx.timerService().currentProcessingTime();
                        long timeoutTime = currentTime + Long.parseLong(rule.getTimeout());
                        ctx.timerService().registerProcessingTimeTimer(timeoutTime);
                        lastEvent.update(keyed.getWrapped());
                        lastTimer.update(timeoutTime);
                        lastAlert.update(rule);
                    }
                }
            }

            private Optional<JsonObject> evaluateRule(ReadOnlyContext ctx, long processingTime, Rule rule) {
                try {

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
                        jsonObject.addProperty("ruleId", rule.getRuleId());
                        jsonObject.addProperty("ruleConditionJsonPath", rule.getRuleConditionJsonPath());
                        jsonObject.addProperty("detail", formatDetail(rule, payloads));
                        return Optional.of(jsonObject);
                    }
                } catch (Exception e) {
                    log.error("Error while evaluating Rule", e);
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
                for (Long eventTime : windowState.keys()) {
                    Event event = windowState.get(eventTime);
                    if (isStateValueInWindow(eventTime, windowStartForEvent, currentEventTime)) {
                        jsonArray.add(event.getPayload());
                    }
                }
                return jsonArray;
            }

            @Override
            public void processBroadcastElement(Rule rule, Context context, Collector<JsonObject> collector) throws Exception {
                System.out.println(rule);
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

                raiseUnreachableAlert(timestamp, out);
            }

            private void raiseUnreachableAlert(long timestamp, Collector<JsonObject> out) throws IOException {
                if (lastTimer.value() != null && timestamp == lastTimer.value()) {
                    Event event = lastEvent.value();
                    Rule rule = lastAlert.value();
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("ruleId", rule.getRuleId());
                    jsonObject.addProperty("timeout", rule.getTimeout());
                    jsonObject.addProperty("detail", formatDetail(rule, event.payload));
                    out.collect(jsonObject);
                }
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

            private boolean isStateValueInWindow(Long stateEventTime, Long windowStartForEvent, long currentEventTime) {
                return stateEventTime >= windowStartForEvent && stateEventTime <= currentEventTime;
            }

            private void updateWidestWindowRule(Rule rule, Context context) throws Exception {
                Long windowSize = rule.getWindowSize();
                if (windowSize != null) {
                    updateWidestWindowAlert(context, windowSize);
                } else {
                    Long eventCount = rule.getEventCount();
                    if (eventCount != null) {
                        updateWidestEventCountAlert(context, eventCount);
                    }
                }
            }

            private void updateWidestWindowAlert(Context context, Long windowSize) throws Exception {
                Rule jsonObject = context.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_ALERT_KEY);
                if (jsonObject == null) {
                    context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_ALERT_KEY, jsonObject);
                } else {
                    Long widestWindow = jsonObject.getWindowSize();
                    if (widestWindow < windowSize) {
                        context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_ALERT_KEY, jsonObject);
                    }
                }
            }

            private void updateWidestEventCountAlert(Context context, Long eventCount) throws Exception {
                Rule jsonObject = context.getBroadcastState(Descriptors.rulesDescriptor).get(WIDEST_EVENT_COUNT_KEY);
                if (jsonObject == null) {
                    context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_EVENT_COUNT_KEY, jsonObject);
                } else {
                    Long widestWindow = jsonObject.getEventCount();
                    if (widestWindow < eventCount) {
                        context.getBroadcastState(Descriptors.rulesDescriptor).put(WIDEST_EVENT_COUNT_KEY, jsonObject);
                    }
                }
            }
        };
    }

    private static String formatDetail(Rule rule, JsonObject payloads) {
        Pattern pattern = Pattern.compile("@.*@");
        String detail = rule.getDetail();
        Matcher matcher = pattern.matcher(detail);
        while(matcher.find()) {
            final String stringToReplace = detail.subSequence(matcher.start(), matcher.end()).toString();
            String jsonPath = stringToReplace.replace("@", "").trim();
            detail = detail.replace(stringToReplace,JsonPath.from(payloads.toString()).get(jsonPath));
        }
        return detail;
    }

    @NotNull
    private static Function<Integer, Rule> mapToRule(KeyedBroadcastProcessFunction<Event, Keyed<Event, String, Integer>, Rule, JsonObject>.ReadOnlyContext ctx) {
        return ruleId -> {
            try {
                return ctx.getBroadcastState(Descriptors.rulesDescriptor).get(ruleId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}