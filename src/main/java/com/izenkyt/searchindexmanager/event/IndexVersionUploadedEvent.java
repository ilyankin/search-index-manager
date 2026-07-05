package com.izenkyt.searchindexmanager.event;

import java.time.Instant;
import java.util.UUID;

public record IndexVersionUploadedEvent(
        UUID versionId,
        UUID indexId,
        int versionNumber,
        String artifactKey,
        long artifactSize,
        String checksum,
        Instant occurredAt) {
}
