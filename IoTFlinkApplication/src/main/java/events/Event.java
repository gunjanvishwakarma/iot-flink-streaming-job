package events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
class Event {
    @JsonProperty("shape")
    private String shape;

    @JsonProperty("border")
    private String border;

    @JsonProperty("x")
    private Long x;

    @JsonProperty("y")
    private Long y;

    @JsonProperty("color")
    private String color;
}
