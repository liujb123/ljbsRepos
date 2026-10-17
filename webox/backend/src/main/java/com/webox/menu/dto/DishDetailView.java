package com.webox.menu.dto;

import com.webox.dish.Allergen;
import com.webox.dish.DishCategory;
import com.webox.dish.ProteinType;
import com.webox.dish.SelectionType;
import com.webox.dish.SpiceLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Full dish detail (PRD 3.2): description, ingredients and the customization groups. */
public record DishDetailView(
        Long dishId,
        String name,
        String description,
        BigDecimal price,
        DishCategory category,
        SpiceLevel spiceLevel,
        String imageUrl,
        Set<ProteinType> proteins,
        Set<Allergen> allergens,
        boolean available,
        int dailyQty,
        int remainingQty,
        List<OptionGroupView> optionGroups) {

    public record OptionGroupView(
            Long groupId,
            String name,
            boolean required,
            SelectionType selectionType,
            List<OptionView> options) {
    }

    public record OptionView(
            Long optionId,
            String name,
            BigDecimal priceDelta,
            boolean available) {
    }
}
