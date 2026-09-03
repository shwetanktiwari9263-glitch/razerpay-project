package com.recoveryagent.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PaymentStatusConverter implements AttributeConverter<PaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(PaymentStatus value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public PaymentStatus convertToEntityAttribute(String value) {
        return value == null ? null : PaymentStatus.valueOf(value.toUpperCase());
    }
}
