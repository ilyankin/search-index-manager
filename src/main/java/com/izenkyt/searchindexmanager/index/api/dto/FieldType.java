package com.izenkyt.searchindexmanager.index.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Тип поля Lucene. Определяет, как значения документа для этого поля анализируются и индексируются.")
public enum FieldType {
    @Schema(description = "Индексируется как единый неанализируемый токен — для точных совпадений, фильтрации и сортировки (ID, статусы, теги).")
    @JsonProperty("keyword") KEYWORD,
    @Schema(description = "Анализируется и разбивается на токены для полнотекстового поиска.")
    @JsonProperty("text") TEXT,
    @Schema(description = "Индексируется как 64-битное целое число, поддерживает сортировку и поиск по диапазону.")
    @JsonProperty("long") LONG,
    @Schema(description = "Индексируется как число с плавающей точкой двойной точности, поддерживает сортировку и поиск по диапазону.")
    @JsonProperty("double") DOUBLE,
    @Schema(description = "Индексируется как булев флаг.")
    @JsonProperty("boolean") BOOLEAN;

    public String toStorageValue() {
        return name().toLowerCase();
    }

    public static FieldType fromStorageValue(String value) {
        return FieldType.valueOf(value.toUpperCase());
    }
}