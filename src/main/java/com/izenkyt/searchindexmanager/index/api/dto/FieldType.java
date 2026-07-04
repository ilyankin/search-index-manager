package com.izenkyt.searchindexmanager.index.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FieldType {
    @JsonProperty("keyword") KEYWORD,
    @JsonProperty("text") TEXT,
    @JsonProperty("long") LONG,
    @JsonProperty("double") DOUBLE,
    @JsonProperty("boolean") BOOLEAN;

    public String toStorageValue() {
        return name().toLowerCase();
    }

    public static FieldType fromStorageValue(String value) {
        return FieldType.valueOf(value.toUpperCase());
    }
}