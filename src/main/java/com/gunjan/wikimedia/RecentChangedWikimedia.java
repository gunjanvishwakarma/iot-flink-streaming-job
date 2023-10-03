package com.gunjan.wikimedia;

import java.io.Serializable;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RecentChangedWikimedia implements Serializable
{
    @JsonProperty(value = "timestamp")
    private long timestamp_ms;

    @JsonProperty(value = "bot")
    private Boolean bot;

    @JsonProperty(value = "server_name")
    private String server_name;
    
    private String fullJson;

    public String getText() {
        return fullJson;
    }

    public void setText(String text) {
        this.fullJson = text;
    }

    public long getTimestamp_ms() {
        return timestamp_ms;
    }

    public void setTimestamp_ms(long timestamp_ms) {
        this.timestamp_ms = timestamp_ms;
    }

    public Boolean getBot() {
        return bot;
    }

    public void setBot(Boolean bot) {
        this.bot = bot;
    }

    public String getFullJson() {
        return fullJson;
    }

    public void setFullJson(String fullJson) {
        this.fullJson = fullJson;
    }

    public String getServer_name() {
        return server_name;
    }

    public void setServer_name(String server_name) {
        this.server_name = server_name;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Wikimedia{");
        sb.append(", timestamp_ms=").append(timestamp_ms);
        sb.append(", bot=").append(bot);
        sb.append('}');
        return sb.toString();
    }
}
