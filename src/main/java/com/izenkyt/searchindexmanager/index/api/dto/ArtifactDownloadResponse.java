package com.izenkyt.searchindexmanager.index.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Временная presigned-ссылка на скачивание архива артефакта версии индекса из MinIO.")
public record ArtifactDownloadResponse(
        @Schema(description = "Presigned URL для прямого скачивания tar.gz-архива артефакта.",
                example = "http://localhost:9000/search-index-artifacts/products/1/index.tar.gz?X-Amz-...")
        String url,
        @Schema(description = "Момент времени, после которого ссылка становится недействительной.")
        Instant expiresAt
) {
}
