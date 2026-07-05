package com.izenkyt.searchindexmanager.indexload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@ConfigurationProperties(prefix = "search.index.load")
@Validated
public record IndexLoadProperties(
        @NotBlank
        @DefaultValue("./load-work")
        String dir,

        @Valid
        @DefaultValue
        Executor executor) {

    public Path targetDir(UUID indexId, int version) {
        return Path.of(dir).resolve(indexId.toString()).resolve(String.valueOf(version));
    }

    public record Executor(
            @Positive
            @DefaultValue("1")
            int corePoolSize,

            @Positive
            @DefaultValue("2")
            int maxPoolSize,

            @PositiveOrZero
            @DefaultValue("50")
            int queueCapacity,

            @NotBlank
            @DefaultValue("load-")
            String threadNamePrefix,

            @DurationMin(seconds = 1)
            @DefaultValue("25s")
            Duration awaitTermination) {
    }
}
