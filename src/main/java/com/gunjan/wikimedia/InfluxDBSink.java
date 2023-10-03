/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gunjan.wikimedia;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.write.Point;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

public class InfluxDBSink extends RichSinkFunction<Point> {

    private InfluxDBClient influxDBClient;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        final String url = "http://localhost:8086";
        final char[] tokens = "t3mBVUr01hz7y9aMphi3_9NwTbzlcG-QgxECzJbzrlEdi_tj1RVDfakF6SNRsLrqQ_IWaZj7pMSVJNBBfd4dfg==".toCharArray();
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