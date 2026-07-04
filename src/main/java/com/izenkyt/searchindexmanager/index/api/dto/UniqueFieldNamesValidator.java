package com.izenkyt.searchindexmanager.index.api.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.Set;

public class UniqueFieldNamesValidator implements ConstraintValidator<UniqueFieldNames, CreateIndexRequest> {

    @Override
    public boolean isValid(CreateIndexRequest request, ConstraintValidatorContext context) {
        if (request == null || request.fields() == null) {
            return true;
        }
        Set<String> seen = new HashSet<>();
        boolean hasDuplicate = false;
        for (FieldDefinition field : request.fields()) {
            if (field != null && field.name() != null && !seen.add(field.name())) {
                hasDuplicate = true;
                break;
            }
        }
        if (hasDuplicate) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("fields")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
