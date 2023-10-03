package com.gunjan.alerting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "ruleId",
        "groupingKeyJsonPaths",
        "windowSize",
        "eventCount",
        "ruleCondition",
        "devices",
        "deviceIdJsonPath",
        "detail"
})
@Data
@EqualsAndHashCode(callSuper = false)
public class Rule {
    @JsonProperty("ruleId")
    private Integer ruleId;

    @JsonProperty("groupingKeyJsonPaths")
    private List<String> groupingKeyJsonPaths;

    @JsonProperty("windowSize")
    private Long windowSize;

    @JsonProperty("eventCount")
    private Long eventCount;

    @JsonProperty("ruleConditionJsonPath")
    private String ruleConditionJsonPath;

    @JsonProperty("devices")
    private List<String> devices;

    @JsonProperty("deviceIdJsonPath")
    private String deviceIdJsonPath;

    @JsonProperty("detail")
    private String detail;

    @JsonProperty("ruleType")
    private String ruleType;

    @JsonProperty("timeout")
    private String timeout;
}