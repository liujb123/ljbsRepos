package com.webox.converter;

import com.webox.dish.Allergen;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the allergen label ({@code Peanuts}, {@code Shellfish}, ...). */
@Converter(autoApply = true)
public class AllergenConverter implements AttributeConverter<Allergen, String> {

    @Override
    public String convertToDatabaseColumn(Allergen attribute) {
        return attribute == null ? null : attribute.label();
    }

    @Override
    public Allergen convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Allergen.fromLabel(dbData);
    }
}
