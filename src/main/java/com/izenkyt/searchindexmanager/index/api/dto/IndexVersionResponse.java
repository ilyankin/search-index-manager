package com.izenkyt.searchindexmanager.index.api.dto;

import java.time.Instant;
import java.util.UUID;

public record IndexVersionResponse(
        UUID id,
        int version,
        String status,
        Long docCount,
        String artifactKey,
        Long artifactSize,
        String checksum,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}