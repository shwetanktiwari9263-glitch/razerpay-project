package com.recoveryagent.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RootCauseCategoryConverter implements AttributeConverter<RootCauseCategory, String> {

    @Override
    public String convertToDatabaseColumn(RootCauseCategory value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public RootCauseCategory convertToEntityAttribute(String value) {
        return value == null ? null : RootCauseCategory.valueOf(value.toUpperCase());
    }
}
