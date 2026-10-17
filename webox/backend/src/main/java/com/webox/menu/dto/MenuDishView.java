package com.webox.menu.dto;

import com.webox.dish.Allergen;
import com.webox.dish.Dish;
import com.webox.dish.DishCategory;
import com.webox.dish.ProteinType;
import com.webox.dish.SpiceLevel;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The user-independent part of a menu card, and the exact payload stored in Redis.
 *
 * <p>Nothing in this record depends on the caller, which is what makes a single cached
 * snapshot safe to serve to every employee. Per-user decoration (recommendation flags)
 * happens after the cache read, in {@link MenuItemView}.
 */
public record MenuDishView(
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
        boolean hasOptions) {

    public static MenuDishView from(Dish dish, int dailyQty, int remainingQty) {
        return new MenuDishView(
                dish.getId(),
                dish.getName(),
                dish.getDescription(),
                dish.getPrice(),
                dish.getCategory(),
                dish.getSpiceLevel(),
                dish.getImageUrl(),
                new LinkedHashSet<>(dish.getProteins()),
                new LinkedHashSet<>(dish.getAllergens()),
                dailyQty,
                remainingQty,
                // Lets the menu card decide between adding directly and sending the employee
                // to the detail page to answer the required choices.
                !dish.getOptionGroups().isEmpty());
    }

    /** Card/detail payload with the caller-specific recommendation decoration applied. */
    public MenuItemView decorate(boolean recommended, List<String> matchTags) {
        return new MenuItemView(dishId, name, description, price, category, spiceLevel, imageUrl,
                proteins, allergens, dailyQty, remainingQty, hasOptions, recommended, matchTags);
    }
}
