package com.gunjan.alerting;

import com.google.gson.JsonObject;
import lombok.Data;

@Data
public class Event {
    JsonObject payload;
}
