package com.gunjan.alerting.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

class Events {
    @JsonProperty("data")
    private List<Event> data = new ArrayList<>();

    public List<Event> getData() {
        return data;
    }

    public void setData(List<Event> data) {
        this.data = data;
    }
}
