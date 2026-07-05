package com.izenkyt.searchindexmanager.storage;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "search.index.storage")
@Validated
public record MinioStorageProperties(
        @NotBlank
        @DefaultValue("http://localhost:9000")
        String endpoint,

        @NotBlank
        @DefaultValue("http://localhost:9000")
        String publicEndpoint,

        @NotBlank
        String accessKey,

        @NotBlank
        String secretKey,

        @NotBlank
        String bucket,

        @DurationMin(seconds = 1)
        @DurationMax(days = 7)
        @DefaultValue("15m")
        Duration presignTtl) {
}
