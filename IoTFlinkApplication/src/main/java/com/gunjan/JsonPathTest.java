package com.gunjan;



import com.jayway.jsonpath.DocumentContext;
import io.restassured.path.json.JsonPath;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonPathTest {

    public static void main(String[] args) {
        String detail = "{\n" +
                "  \"severity\": \"MAJOR\",\n" +
                "  \"name\": \"Sensor unreachable\",\n" +
                "  \"description\": \"Sensor unreachable\",\n" +
                "  \"devEui\": \"@     payloads[0].devEUI        @\"\n" +
                "}";
        Pattern pattern = Pattern.compile("@.*@");
        Matcher matcher = pattern.matcher(detail);
        while(matcher.find()) {
            System.out.println(detail.subSequence(matcher.start(),matcher.end()).toString().replace("@", "").trim());
        }

    }
    public static void main1(String[] args) {

        final String JSON = "{\"payloads\" : [{\n" +
                "  \"applicationID\": \"39\",\n" +
                "  \"applicationName\": \"APP-f002faf8-9ae0-40be-aefa-780095bb6ca1-EU868\",\n" +
                "  \"deviceName\": \"AV202_00002\",\n" +
                "  \"devEUI\": \"1000000000000002\",\n" +
                "  \"rxInfo\": [\n" +
                "    {\n" +
                "      \"gatewayID\": \"1000000000000006\",\n" +
                "      \"uplinkID\": \"54984a20-b12a-4e42-9f5a-993ee1760e59\",\n" +
                "      \"name\": \"IXM14\",\n" +
                "      \"rssi\": 50,\n" +
                "      \"loRaSNR\": 5.5,\n" +
                "      \"location\": {\n" +
                "        \"latitude\": 12.9130209,\n" +
                "        \"longitude\": 77.6439489,\n" +
                "        \"altitude\": 0\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"txInfo\": {\n" +
                "    \"frequency\": 868100000,\n" +
                "    \"dr\": 2\n" +
                "  },\n" +
                "  \"adr\": false,\n" +
                "  \"fCnt\": 1,\n" +
                "  \"fPort\": 6,\n" +
                "  \"data\": \"AZUBJAAAAAAAAAA=\",\n" +
                "  \"object\": {\n" +
                "    \"batteryVoltage\": 3.6,\n" +
                "    \"fPort\": 6,\n" +
                "    \"rawPayload\": \"0195012400000000000000\",\n" +
                "    \"temperatureeee\": \"0.00\"\n" +
                "  }}]} ";
        {
           // System.out.println(JsonPath.parse(JSON).set("$.payloads[0].applicationID", "10000").json().toString());

            //final Object o = JsonPath.from(JSON).get("payloads.findAll { payload -> (payload.occupancy == 'available' && payload.illuminance == 184)}");
            //final Object o = JsonPath.from(JSON).get("payloads.findAll { true }.size() > 10");

            final Object o = JsonPath.from(JSON).get("payloads[0].devEUI");
            System.out.println(o);
            //JsonPath.parse(Body).set(fieldPath, Value);
            //System.out.println(o);
        }

    }
}
