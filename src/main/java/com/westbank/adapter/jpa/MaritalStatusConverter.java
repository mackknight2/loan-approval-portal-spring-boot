package com.westbank.adapter.jpa;

import com.westbank.entity.MaritalStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MaritalStatusConverter implements AttributeConverter<MaritalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(MaritalStatus maritalStatus) {
        if (maritalStatus != null)
            return maritalStatus.getDbValue();
        return MaritalStatus.OTHER.getDbValue();
    }

    @Override
    public MaritalStatus convertToEntityAttribute(Integer s) {
        if (s == null)
            return null;
        for (MaritalStatus value : MaritalStatus.values()) {
            if (value.getDbValue() == s)
                return value;
        }
        throw new IllegalArgumentException("Unknown value " + s);
    }
}
