package com.gunjan.alerting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gunjan.alerting.template.Template;
import com.influxdb.client.write.Point;
import io.restassured.path.json.JsonPath;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.util.Collector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Properties;

/**
 * A basic Kinesis Data Analytics for Java application with Kinesis data
 * streams as source and sink.
 */
public class IoTAlertingJob_V3 {
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
                                .broadcast(new MapStateDescriptor<>("AlertsBroadcastState", BasicTypeInfo.STRING_TYPE_INFO, TypeInformation.of(new TypeHint<Template>() {})))
                )
                .process(processFunction())
                .map((MapFunction<JsonObject, Point>) payload -> Point.measurement("alarm")
                        .addField("name", payload.get("name").getAsString())
                        .addField("description", payload.get("description").getAsString())
                        .addField("severity", payload.get("severity").getAsString())
                        .addField("jsonPath", payload.get("jsonPath").getAsString())
                        .addField("devicePayload", payload.get("devicePayload").getAsString())).addSink(new InfluxDBSink());

        executionEnvironment.execute("IoTDataProcessing");

    }

    @NotNull
    private static KeyedBroadcastProcessFunction<String, JsonObject, JsonObject, JsonObject> processFunction() {
        return new KeyedBroadcastProcessFunction<String, JsonObject, JsonObject, JsonObject>() {

            private final MapStateDescriptor<String, JsonObject> alertStateDescriptor =
                    new MapStateDescriptor<>(
                            "AlertsBroadcastState",
                            BasicTypeInfo.STRING_TYPE_INFO,
                            TypeInformation.of(new TypeHint<JsonObject>() {
                            }));

            @Override
            public void processElement(JsonObject devicePayload, ReadOnlyContext readOnlyContext, Collector<JsonObject> generatedAlert) throws Exception {
                for (Map.Entry<String, JsonObject> entry :
                        readOnlyContext.getBroadcastState(alertStateDescriptor).immutableEntries()) {
                    final String devEui = entry.getKey();
                    if (devEui != null && devEui.equals(devicePayload.get("devEUI").getAsString())) {
                        JsonObject template = entry.getValue();
                        template.get("alerts").getAsJsonArray().forEach(jsonElement -> {
                            JsonObject alertDetail = jsonElement.getAsJsonObject();
                            final Boolean isCreateAlarm = JsonPath.from(devicePayload.toString()).get(alertDetail.get("jsonPath").getAsString());
                            if (isCreateAlarm) {
                                JsonObject jsonObject = new JsonObject();
                                jsonObject.addProperty("name", alertDetail.get("name").getAsString());
                                jsonObject.addProperty("description", alertDetail.get("description").getAsString());
                                jsonObject.addProperty("severity", alertDetail.get("severity").getAsString());
                                jsonObject.addProperty("jsonPath", alertDetail.get("jsonPath").getAsString());
                                jsonObject.addProperty("devicePayload", devicePayload.toString());
                                generatedAlert.collect(jsonObject);
                            }
                        });
                    }
                }
            }

            @Override
            public void processBroadcastElement(JsonObject jsonPath, Context context, Collector<JsonObject> collector) throws Exception {
                final JsonArray devices = jsonPath.get("devices").getAsJsonArray();
                for (JsonElement jsonElement : devices) {
                    context.getBroadcastState(alertStateDescriptor).put(jsonElement.getAsString(), jsonPath);
                }
            }
        };
    }
}