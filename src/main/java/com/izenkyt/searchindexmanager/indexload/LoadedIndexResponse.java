package com.izenkyt.searchindexmanager.indexload;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Запись реестра загруженных в живой Lucene-индекс версий. Реестр локален для "
        + "этого инстанса приложения (in-memory) и не отражает никакой статус версии в БД.")
public record LoadedIndexResponse(
        @Schema(description = "Идентификатор индекса.")
        UUID indexId,
        @Schema(description = "Номер загруженной версии.", example = "1")
        int version,
        @Schema(description = "Состояние загрузки в этом инстансе.", allowableValues = {"LOADING", "LOADED", "FAILED"})
        String state,
        @Schema(description = "Количество документов в открытом индексе. Заполняется после LOADED.", nullable = true)
        Long numDocs,
        @Schema(description = "Размер распакованного индекса на диске в байтах. Заполняется после LOADED.", nullable = true)
        Long sizeOnDiskBytes,
        @Schema(description = "Текст ошибки, если загрузка завершилась статусом FAILED.", nullable = true)
        String error,
        @Schema(description = "Момент, когда загрузка завершилась статусом LOADED.", nullable = true)
        Instant loadedAt
) {
}
