package com.gunjan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.write.Point;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.ContinuousProcessingTimeTrigger;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.util.Collector;
import scala.Tuple2;

import java.util.Properties;

/**
 * A basic Kinesis Data Analytics for Java application with Kinesis data
 * streams as source and sink.
 */
public class IoTStreamingJob {
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

        FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<>("test", new SimpleStringSchema(), properties);

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.addSource(consumer)
        //createSourceFromStaticConfig(env)
                .map((MapFunction<String, FlinkDevicePayload>) payload -> new ObjectMapper().readValue(payload, FlinkDevicePayload.class))
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<FlinkDevicePayload>(Time.seconds(300)) {
                    @Override
                    public long extractTimestamp(FlinkDevicePayload element) {
                        return element.getReceived_at();
                    }
                })
                .timeWindowAll(Time.seconds(30), Time.seconds(5))
                .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
                .apply(new AllWindowFunction<FlinkDevicePayload, Tuple2<Long, Long>, TimeWindow>() {
                    Double totalTemp = 0.0;
                    Long count = 0L;
                    @Override
                    public void apply(TimeWindow timeWindow, Iterable<FlinkDevicePayload> iterable, Collector<Tuple2<Long, Long>> collector) throws Exception {
                        iterable.forEach(flinkDevicePayload -> {
                            final Double temperature = Double.valueOf(String.valueOf(flinkDevicePayload.getPayloadJson().get("temperature")));
                            totalTemp += temperature;
                            count++;
                        });
                        final Long l = totalTemp.longValue() / count.longValue();

                        collector.collect(new Tuple2<>(l, timeWindow.maxTimestamp()));
                    }
                }).map((MapFunction<Tuple2<Long, Long>, Point>) longLongTuple2 -> Point.measurement("avg_temperature")
                        .addField("value", String.valueOf(longLongTuple2._1))
                        //.addField("time", longLongTuple2._2)).addSink(new InfluxDBSink());
                        .addField("time", longLongTuple2._2)).addSink(new SinkFunction<Point>() {
            @Override
            public void invoke(Point value, Context context) throws Exception {
                System.out.println(value.toLineProtocol());
            }
        });

        env.execute("IoTDataProcessing");
    }
}


