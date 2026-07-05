package com.izenkyt.searchindexmanager.event;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "search.index.events")
@Validated
public record IndexEventsProperties(
        @NotBlank
        @DefaultValue("index-version-uploaded")
        String topic,

        @DurationMin(seconds = 1)
        @DefaultValue("10s")
        Duration sendTimeout) {

    public String deadLetterTopic() {
        return topic + ".DLT";
    }
}
