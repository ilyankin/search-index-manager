package com.izenkyt.searchindexmanager.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "search.index.events")
public class IndexEventsProperties {

    private String topic = "index-version-uploaded";

    private Duration sendTimeout = Duration.ofSeconds(10);

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public String deadLetterTopic() {
        return topic + ".DLT";
    }
}
