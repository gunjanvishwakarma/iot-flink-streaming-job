
package com.gunjan.alerting;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "frequency",
    "dr"
})
@Data
public class TxInfo {

    @JsonProperty("frequency")
    public Integer frequency;
    @JsonProperty("dr")
    public Integer dr;

}
