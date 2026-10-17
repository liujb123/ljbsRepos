package com.webox.converter;

import com.webox.dish.ProteinType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the protein label ({@code Chicken}, {@code Tofu}, ...). */
@Converter(autoApply = true)
public class ProteinTypeConverter implements AttributeConverter<ProteinType, String> {

    @Override
    public String convertToDatabaseColumn(ProteinType attribute) {
        return attribute == null ? null : attribute.label();
    }

    @Override
    public ProteinType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProteinType.fromLabel(dbData);
    }
}
