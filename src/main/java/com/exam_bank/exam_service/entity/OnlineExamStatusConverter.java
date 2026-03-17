package com.exam_bank.exam_service.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class OnlineExamStatusConverter implements AttributeConverter<OnlineExamStatus, String> {

    @Override
    public String convertToDatabaseColumn(OnlineExamStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public OnlineExamStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OnlineExamStatus.valueOf(dbData.toUpperCase());
    }
}
