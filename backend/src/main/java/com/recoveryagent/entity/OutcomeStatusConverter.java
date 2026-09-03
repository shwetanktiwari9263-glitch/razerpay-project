package com.recoveryagent.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OutcomeStatusConverter implements AttributeConverter<OutcomeStatus, String> {

    @Override
    public String convertToDatabaseColumn(OutcomeStatus value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public OutcomeStatus convertToEntityAttribute(String value) {
        return value == null ? null : OutcomeStatus.valueOf(value.toUpperCase());
    }
}
