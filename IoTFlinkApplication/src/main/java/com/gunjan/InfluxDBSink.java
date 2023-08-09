package com.gunjan;

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

//    @Override
//    public void open(Configuration parameters) throws Exception {
//        super.open(parameters);
//        Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
//        Properties influxDBConfigProperties = applicationProperties.get("InfluxDBConfigProperties");
//        final String url = influxDBConfigProperties.getProperty("url");
//        final char[] tokens = influxDBConfigProperties.getProperty("token").toCharArray();
//        final String org = influxDBConfigProperties.getProperty("org");
//        final String bucket = influxDBConfigProperties.getProperty("bucket");
//        InfluxDBClient influxDBClient = InfluxDBClientFactory.create(url, tokens, org, bucket);
//        this.influxDBClient = influxDBClient;
//    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        final String url = "http://localhost:8086";
        final char[] tokens = "gVmZTgfbLYCUd4XIGXUXiDQfQiI3e2voPpC20SJBcYx_3V22N9z3n9VeuTHtyBDd_o4nLk5WDyHSYNNeRoLghQ==".toCharArray();
        final String org = "gunjan-org";
        final String bucket = "gunjan-bucket";
        InfluxDBClient influxDBClient = InfluxDBClientFactory.create(url, tokens, org, bucket);
        this.influxDBClient = influxDBClient;
    }

    @Override
    public void invoke(Point value, Context context) throws Exception {
        System.out.println(value.toLineProtocol());
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