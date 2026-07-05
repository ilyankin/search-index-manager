package com.izenkyt.searchindexmanager.index.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Версия индекса, созданная в результате запуска сборки. "
        + "Отражает текущий статус фонового процесса построения и выгрузки артефакта.")
public record IndexVersionResponse(
        @Schema(description = "Идентификатор версии.")
        UUID id,
        @Schema(description = "Порядковый номер версии внутри индекса, возрастает с каждой новой сборкой.", example = "1")
        int version,
        @Schema(description = "Статус жизненного цикла версии.",
                allowableValues = {"CREATED", "BUILDING", "BUILT", "UPLOADED", "READY", "FAILED"})
        String status,
        @Schema(description = "Количество документов, попавших в индекс. Заполняется после успешной сборки.", nullable = true)
        Long docCount,
        @Schema(description = "Ключ (путь) артефакта в MinIO вида {index}/{version}/index.tar.gz. "
                + "Заполняется после выгрузки.", nullable = true)
        String artifactKey,
        @Schema(description = "Размер артефакта в байтах.", nullable = true)
        Long artifactSize,
        @Schema(description = "Контрольная сумма (checksum) артефакта для проверки целостности.", nullable = true)
        String checksum,
        @Schema(description = "Текст ошибки, если сборка или выгрузка завершились статусом FAILED.", nullable = true)
        String errorMessage,
        @Schema(description = "Дата и время создания версии (начала сборки).")
        Instant createdAt,
        @Schema(description = "Дата и время последнего изменения статуса версии.")
        Instant updatedAt
) {
}