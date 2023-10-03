package com.gunjan.alerting;



import io.restassured.path.json.JsonPath;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;

public class JsonPathTest {

    public static void main3(String[] args) {
        class Point{
            int x;
            int y;

            public Point(int x, int y) {
                this.x = x;
                this.y = y;
            }
        }

        ArrayList<Point> points = new ArrayList<>();
        points.add(new Point(1, 2));
        points.add(new Point(3, 4));
        points.add(new Point(5, 6));

        final Boolean status = points.stream().collect(new Collector<Point, Point, Boolean>() {
            @Override
            public Supplier<Point> supplier() {
                return () -> new Point(0, 0);
            }

            @Override
            public BiConsumer<Point, Point> accumulator() {
                return (point, point2) -> {
                    point.x = point.x + point2.x;
                    point.y = point.y + point2.y;
                };
            }

            @Override
            public BinaryOperator<Point> combiner() {
                return (point, point2) -> new Point(point.x + point2.x, point.y + point2.y);
            }

            @Override
            public Function<Point, Boolean> finisher() {
                return point -> point.x > 2 && point.y > 4;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.singleton(Characteristics.CONCURRENT);
            }
        });

        System.out.println(status);

    }

    public static void main1(String[] args) {
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
    public static void main(String[] args) {

        final String JSON = "{\n" +
                "  \"payloads\": [\n" +
                "    {\n" +
                "      \"shape\": \"square\",\n" +
                "      \"border\": \"red\",\n" +
                "      \"x\": 4,\n" +
                "      \"y\": 3,\n" +
                "      \"color\": \"Violet\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"shape\": \"square\",\n" +
                "      \"border\": \"red\",\n" +
                "      \"x\": 6,\n" +
                "      \"y\": 3,\n" +
                "      \"color\": \"Violet\"\n" +
                "    }\n" +
                "  ]\n" +
                "} ";
        {
            // System.out.println(JsonPath.parse(JSON).set("$.payloads[0].applicationID", "10000").json().toString());

            //final Object o = JsonPath.from(JSON).get("payloads.findAll { payload -> (payload.occupancy == 'available' && payload.illuminance == 184)}");
            //final Object o = JsonPath.from(JSON).get("payloads.findAll { true }.size() > 10");

            //final Object o = JsonPath.from(JSON).get("payloads.inject([0,0], { list, event -> list[0] = list[0] + event.x; list[1] = list[1] + event.y; return list }).inject([:]){ r, i -> r[r.size()] = i; return r}.collect({entry -> if(entry.key == 0) return entry.value > 3 else return entry.value > 10}).inject(false) { a, b -> a && b }");
            //[1, 2, 3, 4].inject(0, { sum, value -> sum + value }):
            //System.out.println(o);
            //JsonPath.parse(Body).set(fieldPath, Value);
            //System.out.println(o);
//            final Object o = JsonPath.from(JSON).get();
//            System.out.println(o);


//            String expression = "\"payloads.findAll { true }.x.sum() > 9\" || (\"payloads.findAll { true }.y.sum() > 9\" || \"payloads.findAll { true }.y.sum() > 9\")";
//            //String expression = "payloads.findAll { true }.x.sum() > 9";
//            Pattern p = Pattern.compile("\"([^\"]*)\"");
//            Matcher m = p.matcher(expression);
//            boolean found = false;
//            while (m.find()) {
//                final String jsonPath = m.group(1);
//                final Boolean o = JsonPath.from(JSON).get(jsonPath);
//                expression = expression.replaceFirst("\"([^\"]*)\"",o.toString());
//                found = true;
//            }
//
//            if(!found){
//                final Object o = JsonPath.from(JSON).get(expression);
//                System.out.println("Result => " + o);
//                return;
//            }
//
//            ScriptEngineManager factory = new ScriptEngineManager();
//            ScriptEngine engine = factory.getEngineByName("JavaScript");
//            try {
//                final Boolean eval = (Boolean) engine.eval(expression);
//                System.out.println("RESULT =>" + eval);
//            } catch (ScriptException e) {
//                throw new RuntimeException(e);
//            }

            final String EVENT = "{\n" +
                    "  \"shape\": \"square\",\n" +
                    "  \"border\": \"red\",\n" +
                    "  \"x\": 4,\n" +
                    "  \"y\": 3,\n" +
                    "  \"color\": \"Violet\"\n" +
                    "}";
            final Object o = JsonPath.from(EVENT).get("shape == 'square' ? 'square' : null");
            //[1, 2, 3, 4].inject(0, { sum, value -> sum + value }):
            System.out.println(o);

        }

    }
}
