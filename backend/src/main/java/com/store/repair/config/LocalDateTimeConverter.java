package com.store.repair.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Converter(autoApply = true)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.format(FORMATTER);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            // Eliminar fraccion de segundos si existe para evitar crashes
            if (dbData.contains(".")) {
                dbData = dbData.substring(0, dbData.indexOf('.'));
            }
            if (dbData.contains("T")) {
                dbData = dbData.replace("T", " ");
            }
            return LocalDateTime.parse(dbData, FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
