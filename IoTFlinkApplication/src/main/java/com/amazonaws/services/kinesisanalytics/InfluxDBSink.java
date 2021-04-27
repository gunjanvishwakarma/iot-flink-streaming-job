package com.amazonaws.services.kinesisanalytics;

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.write.Point;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.util.Map;
import java.util.Properties;

public class InfluxDBSink extends RichSinkFunction<Point> {

    private InfluxDBClient influxDBClient;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
        Properties influxDBConfigProperties = applicationProperties.get("InfluxDBConfigProperties");
        System.out.println(influxDBConfigProperties.getProperty("url"));
        InfluxDBClient influxDBClient = InfluxDBClientFactory.create(influxDBConfigProperties.getProperty("url"), influxDBConfigProperties.getProperty("token").toCharArray(), influxDBConfigProperties.getProperty("org"), influxDBConfigProperties.getProperty("bucket"));
        this.influxDBClient = influxDBClient;
    }

    @Override
    public void invoke(Point value, Context context) throws Exception {
        final WriteApi writeApi1 = influxDBClient.getWriteApi();
        try (WriteApi writeApi = writeApi1) {
            writeApi.writePoint(value);
        }
    }

    @Override
    public void close() {
        influxDBClient.close();
    }
}