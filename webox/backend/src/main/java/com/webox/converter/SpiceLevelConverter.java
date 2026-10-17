package com.webox.converter;

import com.webox.dish.SpiceLevel;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@code None / Mild / Medium / Hot} instead of the Java constant names. */
@Converter(autoApply = true)
public class SpiceLevelConverter implements AttributeConverter<SpiceLevel, String> {

    @Override
    public String convertToDatabaseColumn(SpiceLevel attribute) {
        return attribute == null ? null : attribute.label();
    }

    @Override
    public SpiceLevel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SpiceLevel.fromLabel(dbData);
    }
}
