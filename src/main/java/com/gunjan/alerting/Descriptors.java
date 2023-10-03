package com.gunjan.alerting;

import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;

public class Descriptors {
    public static final MapStateDescriptor<Integer, Rule> rulesDescriptor =
        new MapStateDescriptor<>(
            "rules", BasicTypeInfo.INT_TYPE_INFO, TypeInformation.of(Rule.class));

    public static final MapStateDescriptor<Long, Event> windowStateDescriptor =
            new MapStateDescriptor<>(
                    "windowState",
                    BasicTypeInfo.LONG_TYPE_INFO,
                    TypeInformation.of(new TypeHint<Event>() {
                    }));

    public static final ValueStateDescriptor<Long> lastTimerDescriptor = new ValueStateDescriptor<>("lastTimer", Long.class);
    public static final ValueStateDescriptor<Event> lastFlinkDevicePayloadDescriptor = new ValueStateDescriptor<>("lastFlinkDevicePayload", Event.class);
    public static final ValueStateDescriptor<Rule> lastAlertDescriptor = new ValueStateDescriptor<>("lastAlert", Rule.class);
}