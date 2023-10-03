package com.gunjan.alerting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "applicationID",
    "applicationName",
    "deviceName",
    "devEUI",
    "rxInfo",
    "txInfo",
    "adr",
    "fCnt",
    "fPort",
    "data"
})
@Data
@EqualsAndHashCode(callSuper=false)
public class FlinkDevicePayload {
    @JsonProperty("applicationID")
    private String applicationID;
    @JsonProperty("applicationName")
    private String applicationName;
    @JsonProperty("deviceName")
    private String deviceName;
    @JsonProperty("devEUI")
    private String devEUI;
    @JsonProperty("rxInfo")
    private List<RxInfo> rxInfo = null;
    @JsonProperty("txInfo")
    private TxInfo txInfo;
    @JsonProperty("adr")
    private Boolean adr;
    @JsonProperty("fCnt")
    private Integer fCnt;
    @JsonProperty("fPort")
    private Integer fPort;
    @JsonProperty("data")
    private String data;
    @JsonProperty("object")
    private LinkedHashMap<String,Object> payloadJson;

    private UUID id;

    private String name;

    private String type;

    private String model;

    private long received_at;

}
