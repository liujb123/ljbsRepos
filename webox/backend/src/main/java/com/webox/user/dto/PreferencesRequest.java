package com.webox.user.dto;

import com.webox.dish.Allergen;
import com.webox.dish.DishCategory;
import com.webox.dish.SpiceLevel;
import com.webox.user.TastePreference;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Dietary preferences (PRD 4.1). Sending the full object replaces the stored preferences.
 *
 * <p>All enum values travel as their English labels ("Peanuts", "Light Meal", "Medium").
 */
public record PreferencesRequest(

        Set<Allergen> allergens,

        Set<DishCategory> preferredCategories,

        SpiceLevel spicePreference,

        TastePreference tastePreference,

        @DecimalMin(value = "0.00", message = "Minimum budget cannot be negative.")
        @Digits(integer = 8, fraction = 2, message = "Minimum budget must have at most 2 decimal places.")
        BigDecimal budgetMin,

        @DecimalMin(value = "0.00", message = "Maximum budget cannot be negative.")
        @Digits(integer = 8, fraction = 2, message = "Maximum budget must have at most 2 decimal places.")
        BigDecimal budgetMax) {

    public Set<Allergen> allergensOrEmpty() {
        return allergens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allergens);
    }

    public Set<DishCategory> preferredCategoriesOrEmpty() {
        return preferredCategories == null ? new LinkedHashSet<>() : new LinkedHashSet<>(preferredCategories);
    }
}
