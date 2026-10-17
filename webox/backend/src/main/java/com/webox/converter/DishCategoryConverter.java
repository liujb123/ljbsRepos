package com.webox.converter;

import com.webox.dish.DishCategory;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists the English display label ("Light Meal") rather than the Java constant name
 * ("LIGHT_MEAL"), so the database stays readable and the CHECK constraints in the schema remain
 * self-documenting. Java-side renames therefore never touch stored data.
 */
@Converter(autoApply = true)
public class DishCategoryConverter implements AttributeConverter<DishCategory, String> {

    @Override
    public String convertToDatabaseColumn(DishCategory attribute) {
        return attribute == null ? null : attribute.label();
    }

    @Override
    public DishCategory convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DishCategory.fromLabel(dbData);
    }
}
