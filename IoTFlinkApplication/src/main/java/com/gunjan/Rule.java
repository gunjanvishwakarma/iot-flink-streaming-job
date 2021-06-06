package com.gunjan;

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
        "name",
        "description",
        "severity",
        "devices",
        "deviceIdJsonPath"
})
@Data
@EqualsAndHashCode(callSuper = false)
class Rule {
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
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("severity")
    private String severity;
    @JsonProperty("devices")
    private List<String> devices;
    @JsonProperty("deviceIdJsonPath")
    private String deviceIdJsonPath;
}