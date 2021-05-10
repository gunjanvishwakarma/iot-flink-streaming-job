
package com.gunjan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "gatewayID",
    "uplinkID",
    "name",
    "rssi",
    "loRaSNR",
    "location"
})
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RxInfo {

    @JsonProperty("gatewayID")
    private String gatewayID;
    @JsonProperty("uplinkID")
    private String uplinkID;
    @JsonProperty("name")
    private String name;
    @JsonProperty("rssi")
    private Integer rssi;
    @JsonProperty("loRaSNR")
    private Double loRaSNR;
    @JsonProperty("location")
    private Location location;

}
