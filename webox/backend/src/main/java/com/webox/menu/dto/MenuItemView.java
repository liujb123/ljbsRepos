package com.webox.menu.dto;

import com.webox.dish.Allergen;
import com.webox.dish.DishCategory;
import com.webox.dish.ProteinType;
import com.webox.dish.SpiceLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * A menu card as returned to one caller.
 *
 * <p>{@code recommended} and {@code matchTags} are computed per request from the caller's
 * preferences when "Recommend for me" is on. Prices are plain decimal strings.
 * {@code dailyQty} / {@code remainingQty} are populated from the daily menu row; the
 * deduction path that makes them move is a Tier 3 extension point.
 */
public record MenuItemView(
        Long dishId,
        String name,
        String description,
        BigDecimal price,
        DishCategory category,
        SpiceLevel spiceLevel,
        String imageUrl,
        Set<ProteinType> proteins,
        Set<Allergen> allergens,
        int dailyQty,
        int remainingQty,
        boolean hasOptions,
        boolean recommended,
        List<String> matchTags) {
}
