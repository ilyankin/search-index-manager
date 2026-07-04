package com.izenkyt.searchindexmanager.index.api.dto;

import java.time.Instant;

public record ArtifactDownloadResponse(
        String url,
        Instant expiresAt
) {
}
