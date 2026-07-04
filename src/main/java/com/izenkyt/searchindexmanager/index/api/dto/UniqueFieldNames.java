package com.izenkyt.searchindexmanager.index.api.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueFieldNamesValidator.class)
public @interface UniqueFieldNames {

    String message() default "must not contain duplicate field names";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
