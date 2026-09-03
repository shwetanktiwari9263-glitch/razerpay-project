package com.recoveryagent.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DegradationStatusConverter implements AttributeConverter<DegradationStatus, String> {

    @Override
    public String convertToDatabaseColumn(DegradationStatus value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public DegradationStatus convertToEntityAttribute(String value) {
        return value == null ? null : DegradationStatus.valueOf(value.toUpperCase());
    }
}
