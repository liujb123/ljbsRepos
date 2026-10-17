package com.webox.converter;

import com.webox.user.TastePreference;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@code Light / Balanced / Rich}. */
@Converter(autoApply = true)
public class TastePreferenceConverter implements AttributeConverter<TastePreference, String> {

    @Override
    public String convertToDatabaseColumn(TastePreference attribute) {
        return attribute == null ? null : attribute.label();
    }

    @Override
    public TastePreference convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TastePreference.fromLabel(dbData);
    }
}
