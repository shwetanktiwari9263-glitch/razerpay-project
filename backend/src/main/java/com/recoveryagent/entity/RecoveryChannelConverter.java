package com.recoveryagent.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RecoveryChannelConverter implements AttributeConverter<RecoveryChannel, String> {

    @Override
    public String convertToDatabaseColumn(RecoveryChannel value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public RecoveryChannel convertToEntityAttribute(String value) {
        return value == null ? null : RecoveryChannel.valueOf(value.toUpperCase());
    }
}
