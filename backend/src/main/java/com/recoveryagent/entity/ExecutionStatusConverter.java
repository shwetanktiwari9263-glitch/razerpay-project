package com.recoveryagent.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ExecutionStatusConverter implements AttributeConverter<ExecutionStatus, String> {

    @Override
    public String convertToDatabaseColumn(ExecutionStatus value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public ExecutionStatus convertToEntityAttribute(String value) {
        return value == null ? null : ExecutionStatus.valueOf(value.toUpperCase());
    }
}
