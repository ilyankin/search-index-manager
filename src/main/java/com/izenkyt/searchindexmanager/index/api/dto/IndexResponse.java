package com.izenkyt.searchindexmanager.index.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IndexResponse(
        UUID id,
        String name,
        String description,
        List<FieldDefinition> fields,
        Instant createdAt,
        Instant updatedAt
) {
}