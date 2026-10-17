package com.webox.user.dto;

import com.webox.dish.Allergen;
import com.webox.dish.DishCategory;
import com.webox.dish.SpiceLevel;
import com.webox.user.TastePreference;
import com.webox.user.UserPreference;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/** Current dietary preferences, or empty values when the user has never saved any. */
public record PreferencesResponse(
        Set<Allergen> allergens,
        Set<DishCategory> preferredCategories,
        SpiceLevel spicePreference,
        TastePreference tastePreference,
        BigDecimal budgetMin,
        BigDecimal budgetMax,
        boolean configured) {

    public static PreferencesResponse empty() {
        return new PreferencesResponse(new LinkedHashSet<>(), new LinkedHashSet<>(), null, null, null, null, false);
    }

    public static PreferencesResponse from(UserPreference preference) {
        if (preference == null) {
            return empty();
        }
        boolean configured = !preference.getAllergens().isEmpty()
                || !preference.getPreferredCategories().isEmpty()
                || preference.getSpicePreference() != null
                || preference.getTastePreference() != null
                || preference.getBudgetMin() != null
                || preference.getBudgetMax() != null;
        return new PreferencesResponse(
                new LinkedHashSet<>(preference.getAllergens()),
                new LinkedHashSet<>(preference.getPreferredCategories()),
                preference.getSpicePreference(),
                preference.getTastePreference(),
                preference.getBudgetMin(),
                preference.getBudgetMax(),
                configured);
    }
}
