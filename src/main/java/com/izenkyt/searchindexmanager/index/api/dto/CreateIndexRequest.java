package com.izenkyt.searchindexmanager.index.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateIndexRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z0-9_-]+", message = "must contain only lowercase letters, digits, underscores and hyphens (a-z0-9_-)")
        String name,
        @Size(max = 512)
        String description,
        @NotEmpty
        @Valid
        List<FieldDefinition> fields
) {
}