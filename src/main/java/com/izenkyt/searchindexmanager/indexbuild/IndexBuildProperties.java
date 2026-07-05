package com.izenkyt.searchindexmanager.indexbuild;

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

@ConfigurationProperties(prefix = "search.index.build")
@Validated
public record IndexBuildProperties(
        @NotBlank
        @DefaultValue("./build-work")
        String workdir,

        @Valid
        @DefaultValue
        Executor executor) {

    public Path versionDir(UUID indexId, UUID versionId) {
        return Path.of(workdir).resolve(indexId.toString()).resolve(versionId.toString());
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
            @DefaultValue("build-")
            String threadNamePrefix,

            @DurationMin(seconds = 1)
            @DefaultValue("25s")
            Duration awaitTermination) {
    }
}
