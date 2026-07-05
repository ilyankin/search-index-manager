package com.izenkyt.searchindexmanager.index.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Зарегистрированный индекс и его схема полей.")
public record IndexResponse(
        @Schema(description = "Идентификатор индекса.")
        UUID id,
        @Schema(description = "Уникальное имя индекса.", example = "products")
        String name,
        @Schema(description = "Описание назначения индекса.", example = "Каталог товаров интернет-магазина", nullable = true)
        String description,
        @Schema(description = "Схема полей индекса.")
        List<FieldDefinition> fields,
        @Schema(description = "Дата и время создания индекса.")
        Instant createdAt,
        @Schema(description = "Дата и время последнего изменения индекса.")
        Instant updatedAt
) {
}