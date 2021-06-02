package com.gunjan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gunjan.template.Alert;
import com.gunjan.template.AlertPayload;
import com.gunjan.template.Template;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.util.Collector;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A basic Kinesis Data Analytics for Java application with Kinesis data
 * streams as source and sink.
 */
public class IoTAlertingJob_V2 {
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

        FlinkKafkaConsumer<String> devicePayloadConsumer = new FlinkKafkaConsumer<>("test", new SimpleStringSchema(), properties);




        FlinkKafkaConsumer<String> templateConsumer = new FlinkKafkaConsumer<>("template", new SimpleStringSchema(), properties);


        final StreamExecutionEnvironment executionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment();
        final DataStreamSource<String> devicePayloadDataStreamSource = executionEnvironment.addSource(devicePayloadConsumer);


        KeyedStream<FlinkDevicePayload, String> devicePayloadKeyedByDevEuiStream = devicePayloadDataStreamSource
                .map((MapFunction<String, FlinkDevicePayload>) payload -> new ObjectMapper().readValue(payload, FlinkDevicePayload.class))
                .keyBy((KeySelector<FlinkDevicePayload, String>) flinkDevicePayload -> flinkDevicePayload.getId().toString());

        final SingleOutputStreamOperator<Template> ruleStream = executionEnvironment.addSource(templateConsumer)
                .map((MapFunction<String, Template[]>) payload -> new ObjectMapper().readValue(payload, Template[].class))
                .flatMap(new FlatMapFunction<Template[], Template>() {
                    @Override
                    public void flatMap(Template[] templates, Collector<Template> collector) throws Exception {
                        Arrays.stream(templates).forEach(new Consumer<Template>() {
                            @Override
                            public void accept(Template template) {
                                collector.collect(template);
                            }
                        });
                    }
                })
                ;

        MapStateDescriptor<String, Template> templateStateDescriptor = new MapStateDescriptor<>("TemplatesBroadcastState", BasicTypeInfo.STRING_TYPE_INFO, TypeInformation.of(new TypeHint<Template>() {
        }));

        BroadcastStream<Template> templateBroadcastStream = ruleStream.broadcast(templateStateDescriptor);

        final BroadcastConnectedStream<FlinkDevicePayload, Template> connect = devicePayloadKeyedByDevEuiStream
                .connect(templateBroadcastStream);
        connect.process(

                        new KeyedBroadcastProcessFunction<String, FlinkDevicePayload, Template, AlertPayload>() {

                            private final MapStateDescriptor<String, Template> templateStateDescriptor =
                                    new MapStateDescriptor<>(
                                            "TemplatesBroadcastState",
                                            BasicTypeInfo.STRING_TYPE_INFO,
                                            TypeInformation.of(new TypeHint<Template>() {
                                            }));

                            private transient ValueState<Boolean> isAlarmExist;

                            @Override
                            public void open(Configuration conf) throws IOException {
                                // setup timer state
                                ValueStateDescriptor<Boolean> lastTimerDesc = new ValueStateDescriptor<Boolean>("isAlarmExist", Types.BOOLEAN, false);
                                isAlarmExist = getRuntimeContext().getState(lastTimerDesc);
                            }

                            @Override
                            public void processElement(FlinkDevicePayload flinkDevicePayload, ReadOnlyContext readOnlyContext, Collector<AlertPayload> out) throws Exception {
                                for (Map.Entry<String, Template> entry :
                                        readOnlyContext.getBroadcastState(templateStateDescriptor).immutableEntries()) {
                                    final String sensorUuid = entry.getKey();
                                    if (sensorUuid != null && sensorUuid.equals(flinkDevicePayload.getId().toString())) {
                                        Template template = entry.getValue();
                                        template.getAlerts()
                                                .stream().filter(new Predicate<Alert>() {
                                            @Override
                                            public boolean test(Alert alert) {
                                                return !alert.getName().equals("Sensor unreachable");
                                            }
                                        })
                                                .forEach(new Consumer<Alert>() {
                                            @Override
                                            public void accept(Alert alert) {
                                                String condition = alert.getAdditionalAttributes().get("condition");
                                                try {
                                                    if (isCreateAlarm(condition, flinkDevicePayload)) {
                                                        if(!isAlarmExist.value()){
                                                            String attributeName = condition.split(" ")[0].trim();
                                                            final AlertPayload alertPayload = AlertPayload.builder()
                                                                    .devEui(flinkDevicePayload.getDevEUI())
                                                                    .alarmName(alert.getName())
                                                                    .expression(condition)
                                                                    .currentValue(getCurrentValue(flinkDevicePayload, attributeName))
                                                                    .entityType("Sensor")
                                                                    .isCreated(true)
                                                                    .deviceName(flinkDevicePayload.getName())
                                                                    .emails(alert.getAlertNotification() != null ? alert.getAlertNotification().getEmailsToNotify() : null)
                                                                    .phoneNumbers(alert.getAlertNotification() != null ? alert.getAlertNotification().getPhoneNumbersToNotify() : null).build();
                                                            out.collect(alertPayload);
                                                            isAlarmExist.update(true);
                                                        }

                                                    } else {
                                                        if(isAlarmExist.value()){
                                                            String attributeName = condition.split(" ")[0].trim();
                                                            final AlertPayload alertPayload = AlertPayload.builder()
                                                                    .devEui(flinkDevicePayload.getDevEUI())
                                                                    .alarmName(alert.getName())
                                                                    .expression(condition)
                                                                    .currentValue(getCurrentValue(flinkDevicePayload, attributeName))
                                                                    .entityType("Sensor")
                                                                    .isCreated(false)
                                                                    .deviceName(flinkDevicePayload.getName())
                                                                    .emails(alert.getAlertNotification() != null ? alert.getAlertNotification().getEmailsToNotify() : null)
                                                                    .phoneNumbers(alert.getAlertNotification() != null ? alert.getAlertNotification().getPhoneNumbersToNotify() : null).build();
                                                            out.collect(alertPayload);
                                                            isAlarmExist.update(false);
                                                        }
                                                    }
                                                } catch (ScriptException e) {
                                                    e.printStackTrace();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        });
                                    }
                                }
                            }

                            @Override
                            public void processBroadcastElement(Template template, Context context, Collector<AlertPayload> collector) throws Exception {
                                final List<UUID> devices = template.getDevices();
                                if (devices != null) {
                                    for (UUID uuid : devices) {
                                        context.getBroadcastState(templateStateDescriptor).put(uuid.toString(), template);
                                    }
                                }
                            }
                        }
                )
                .map(new MapFunction<AlertPayload, Point>() {
                    @Override
                    public Point map(AlertPayload payload) throws Exception {
                        return Point.measurement("alarm")
                                .addField("AlarmName", payload.getAlarmName())
                                .addField("Expression", payload.getExpression())
                                .addField("CurrentValue", payload.getCurrentValue())
                                .addField("DevEui", payload.getDevEui())
                                .addField("IsCreated", payload.getIsCreated())
                                .addField("DeviceName", payload.getDeviceName())
                                .addField("Emails", payload.getEmails().stream().collect(Collectors.joining(",")))
                                .addField("PhoneNumbers", payload.getPhoneNumbers().stream().collect(Collectors.joining(",")));
                    }
                }).addSink(new InfluxDBSink());

        connect.process(

                new KeyedBroadcastProcessFunction<String, FlinkDevicePayload, Template, AlertPayload>() {

                    private final MapStateDescriptor<String, Template> templateStateDescriptor1 =
                            new MapStateDescriptor<>(
                                    "TemplatesBroadcastState",
                                    BasicTypeInfo.STRING_TYPE_INFO,
                                    TypeInformation.of(new TypeHint<Template>() {
                                    }));
                    private transient ValueState<Long> lastTimer;

                    private transient ValueState<FlinkDevicePayload> lastFlinkDevicePayload;

                    private transient ValueState<Alert> lastAlert;

                    @Override
                    public void open(Configuration conf) {
                        // setup timer state
                        ValueStateDescriptor<Long> lastTimerDesc = new ValueStateDescriptor<Long>("lastTimer", Long.class);
                        lastTimer = getRuntimeContext().getState(lastTimerDesc);

                        ValueStateDescriptor<FlinkDevicePayload> lastFlinkDevicePayload = new ValueStateDescriptor<FlinkDevicePayload>("lastFlinkDevicePayload", FlinkDevicePayload.class);
                        this.lastFlinkDevicePayload = getRuntimeContext().getState(lastFlinkDevicePayload);

                        ValueStateDescriptor<Alert> lastAlert = new ValueStateDescriptor<Alert>("lastAlert", Alert.class);
                        this.lastAlert = getRuntimeContext().getState(lastAlert);
                    }

                    @Override
                    public void processElement(FlinkDevicePayload flinkDevicePayload, ReadOnlyContext readOnlyContext, Collector<AlertPayload> out1) throws Exception {
                        for (Map.Entry<String, Template> entry :
                                readOnlyContext.getBroadcastState(templateStateDescriptor1).immutableEntries()) {
                            final String sensorUuid = entry.getKey();
                            if (sensorUuid != null && sensorUuid.equals(flinkDevicePayload.getId().toString())) {
                                Template template = entry.getValue();
                                template.getAlerts()
                                        .stream().filter(new Predicate<Alert>() {
                                    @Override
                                    public boolean test(Alert alert) {
                                        return alert.getName().equals("Sensor unreachable");
                                    }
                                })
                                        .forEach(alert -> {
                                            Integer missedUplinkCount = Integer.valueOf(alert.getAdditionalAttributes().get("missed_uplink_count"));

                                            long currentTime = readOnlyContext.timerService().currentProcessingTime();
                                            long timeoutTime = currentTime + (missedUplinkCount * (2 * 60 * 1000));
                                            // register timer for timeout time
                                            readOnlyContext.timerService().registerProcessingTimeTimer(timeoutTime);
                                            // remember timeout time
                                            try {
                                                lastFlinkDevicePayload.update(flinkDevicePayload);
                                                lastTimer.update(timeoutTime);
                                                lastAlert.update(alert);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        });
                            }
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<AlertPayload> out1) throws Exception {
                        // check if this was the last timer we registered
                        if (timestamp == lastTimer.value()) {
                            // it was, so no data was received afterwards.
                            // fire an alert.

                            final FlinkDevicePayload flinkDevicePayload = lastFlinkDevicePayload.value();
                            final Alert alert = lastAlert.value();
                            Integer missedUplinkCount = Integer.valueOf(alert.getAdditionalAttributes().get("missed_uplink_count"));
                            Integer inactivityTimeout = (missedUplinkCount * (2 * 60 * 1000));
                            final AlertPayload alertPayload = AlertPayload.builder()
                                    .devEui(flinkDevicePayload.getDevEUI())
                                    .alarmName("Sensor Unreachable")
                                    .expression("inactivityTimeout >" + inactivityTimeout.toString())
                                    .currentValue(null)
                                    .entityType("Sensor")
                                    .isCreated(true)
                                    .deviceName(flinkDevicePayload.getName())
                                    .emails(alert.getAlertNotification() != null ? alert.getAlertNotification().getEmailsToNotify() : null)
                                    .phoneNumbers(alert.getAlertNotification() != null ? alert.getAlertNotification().getPhoneNumbersToNotify() : null).build();
                            out1.collect(alertPayload);
                        }
                    }

                    @Override
                    public void processBroadcastElement(Template template, Context context, Collector<AlertPayload> collector) throws Exception {
                        final List<UUID> devices = template.getDevices();
                        for (UUID uuid : devices) {
                            context.getBroadcastState(templateStateDescriptor1).put(uuid.toString(), template);
                        }
                    }
                }
        )
                .map(new MapFunction<AlertPayload, Point>() {
                    @Override
                    public Point map(AlertPayload payload) throws Exception {
                        return Point.measurement("alarm")
                                .addField("AlarmName", payload.getAlarmName())
                                .addField("Expression", payload.getExpression())
                                .addField("CurrentValue", payload.getCurrentValue())
                                .addField("DevEui", payload.getDevEui())
                                .addField("IsCreated", payload.getIsCreated())
                                .addField("DeviceName", payload.getDeviceName())
                                .addField("Emails", payload.getEmails().stream().collect(Collectors.joining(",")))
                                .addField("PhoneNumbers", payload.getPhoneNumbers().stream().collect(Collectors.joining(",")));
                    }
                }).addSink(new InfluxDBSink());

        devicePayloadDataStreamSource
                .timeWindowAll(Time.seconds(1))
                .apply((AllWindowFunction<String, Tuple2<Timestamp,Long>, TimeWindow>)(window, values, out) -> {
                    long count = 0;
                    Iterator<String> iterator = values.iterator();
                    while(iterator.hasNext())
                    {
                        iterator.next();
                        count++;
                    }
                    out.collect(new Tuple2<>(new Timestamp(window.getEnd()), count));
                }, TypeInformation.of(new TypeHint<Tuple2<Timestamp,Long>>() {}))
                .map(new MessagePerSecond())
                .addSink(new InfluxDBSink());

        executionEnvironment.execute("IoTDataProcessing");

    }

    private static class MessagePerSecond extends RichMapFunction<Tuple2<Timestamp,Long>,Point>
    {

        @Override
        public Point map(Tuple2<Timestamp,Long> tweetPerSecond) throws Exception
        {
            return Point.measurement("DevicePerSecondCountFlink").time(tweetPerSecond.f0.getTime(), WritePrecision.MS)
                    .addField("count", tweetPerSecond.f1);
        }
    }

    private static boolean isCreateAlarm(String condition, FlinkDevicePayload flinkDevicePayload) throws ScriptException, JsonProcessingException {
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("JavaScript");
        String attributeName = condition.split(" ")[0].trim();
        String attributeValue = getCurrentValue(flinkDevicePayload, attributeName);
        if(attributeValue == null){
            return false;
        }
        if(condition.contains("'")){
            String expression = condition.replaceAll(attributeName, "'"+attributeValue+"'");
            return (Boolean) engine.eval(expression);
        }
        String expression = condition.replaceAll(attributeName, attributeValue);
        return (Boolean) engine.eval(expression);
    }

    @Nullable
    private static String getCurrentValue(FlinkDevicePayload flinkDevicePayload, String attributeName) throws JsonProcessingException {
        String payloadString = getPayloadJsonString(flinkDevicePayload);
        String attributeValue = getSensorAttributeValue(payloadString, attributeName);
        return attributeValue;
    }

    public static String getSensorAttributeValue(String payloadString, String attributeName) throws JsonProcessingException {
        HashMap<String, Object> attributeKeyValue = new ObjectMapper().readValue(payloadString, HashMap.class);
        if (attributeKeyValue.get(attributeName) == null) {
            return null;
        }
        return String.valueOf(attributeKeyValue.get(attributeName));
    }

    private static String getPayloadJsonString(FlinkDevicePayload devicePayload) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(devicePayload.getPayloadJson());
    }
}

