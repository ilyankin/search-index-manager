package com.izenkyt.searchindexmanager.index.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@UniqueFieldNames
@Schema(description = "Запрос на регистрацию нового индекса. Сам индекс при этом не строится — "
        + "только создаётся его описание (схема полей); первая версия появится после вызова эндпоинта build.")
public record CreateIndexRequest(
        @Schema(description = "Уникальное имя индекса.", example = "products", maxLength = 64)
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z0-9_-]+", message = "must contain only lowercase letters, digits, underscores and hyphens (a-z0-9_-)")
        String name,
        @Schema(description = "Произвольное описание назначения индекса.", example = "Каталог товаров интернет-магазина",
                maxLength = 512, nullable = true)
        @Size(max = 512)
        String description,
        @Schema(description = "Схема полей индекса. Должна содержать хотя бы одно поле с уникальным именем.")
        @NotEmpty
        @Valid
        List<FieldDefinition> fields
) {
}