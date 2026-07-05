package com.izenkyt.searchindexmanager.index.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Описание одного поля схемы индекса.")
public record FieldDefinition(
        @Schema(description = "Имя поля, как оно будет использоваться в документах при построении индекса.",
                example = "title", maxLength = 64)
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z0-9_]+", message = "must contain only lowercase letters, digits and underscores (a-z0-9_)")
        String name,

        @Schema(description = "Тип поля, определяющий способ индексирования его значений.")
        @NotNull
        FieldType type
) {
}