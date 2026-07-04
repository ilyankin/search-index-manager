package com.izenkyt.searchindexmanager.index.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FieldDefinition(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z0-9_]+", message = "must contain only lowercase letters, digits and underscores (a-z0-9_)")
        String name,

        @NotNull
        FieldType type
) {
}