package com.exam_bank.exam_service.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class OnlineExamSourceConverter implements AttributeConverter<OnlineExamSource, String> {

    @Override
    public String convertToDatabaseColumn(OnlineExamSource attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public OnlineExamSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OnlineExamSource.valueOf(dbData.toUpperCase());
    }
}
