package com.webox.converter;

import com.webox.dish.DishCategory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds an HTTP query-parameter value to a {@link DishCategory}.
 *
 * <p><b>Not to be confused with {@link DishCategoryConverter}.</b> That class is a JPA
 * {@code AttributeConverter}: it only runs when Hibernate reads or writes a column, and it has no
 * effect whatsoever on request binding. Query parameters are bound by Spring MVC's
 * {@code ConversionService}, whose built-in enum converter matches the Java constant name
 * ({@code CHINESE}) and is case-sensitive - while the API contract, and therefore the SPA, sends
 * the English display label ({@code Chinese}, {@code Light Meal}).
 *
 * <p>Without this converter every {@code ?categories=...} request fails to bind and is rejected
 * with 400. That silently disabled all cuisine filtering: the employee menu filter (PRD 3.2,
 * which requires multi-select) and the Console dish-table filter (PRD 4.3). It stayed hidden
 * because the endpoint still answered with a well-formed error envelope instead of failing loudly.
 *
 * <p>Registering it as a bean is all that is needed: Spring Boot feeds every {@code Converter}
 * bean into the MVC conversion service, and collection binding
 * ({@code @RequestParam List<DishCategory> categories}) reuses it once per element.
 */
@Component
public class DishCategoryRequestConverter implements Converter<String, DishCategory> {

    @Override
    public DishCategory convert(String source) {
        if (source == null) {
            return null;
        }
        DishCategory category = DishCategory.fromLabel(source);
        if (category == null) {
            // Throwing beats returning null here: a null element would survive into
            // MenuService#toCategorySet and blow up inside EnumSet.copyOf as an HTTP 500.
            throw new IllegalArgumentException("Unknown dish category: '" + source + "'");
        }
        return category;
    }
}
