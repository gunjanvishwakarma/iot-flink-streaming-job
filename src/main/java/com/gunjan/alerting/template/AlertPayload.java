package com.gunjan.alerting.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
public class AlertPayload {
        String attributeName;
        String operator;
        String attributeValue;
        String alarmName;
        String unitSymbol;
        Boolean isCreated;
        Boolean isSDPTelemetry = true;
        List<String> emails;
        List<String> phoneNumbers;
        String expression;
        String severity;
        Boolean isUserAlarm;
        Boolean isSystemAlarm;
        String status;
        String entityType;
        String hostname;
        String currentValue;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long lastSeen;
        String description="";

        String devEui;

        String deviceName;

        @Override
        public String toString() {
                return "AlertPayload{" +
                        "attributeName='" + attributeName + '\'' +
                        ", operator='" + operator + '\'' +
                        ", attributeValue='" + attributeValue + '\'' +
                        ", alarmName='" + alarmName + '\'' +
                        ", unitSymbol='" + unitSymbol + '\'' +
                        ", isCreated=" + isCreated +
                        ", isSDPTelemetry=" + isSDPTelemetry +
                        ", expression='" + expression + '\'' +
                        ", severity='" + severity + '\'' +
                        ", isUserAlarm=" + isUserAlarm +
                        ", isSystemAlarm=" + isSystemAlarm +
                        ", status='" + status + '\'' +
                        ", entityType='" + entityType + '\'' +
                        ", hostname='" + hostname + '\'' +
                        ", currentValue='" + currentValue + '\'' +
                        ", lastSeen=" + lastSeen +
                        ", description='" + description + '\'' +
                        '}';
        }
}
