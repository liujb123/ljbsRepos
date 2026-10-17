package com.webox.admin.dto;

import com.webox.dish.Allergen;
import com.webox.dish.Dish;
import com.webox.dish.DishCategory;
import com.webox.dish.DishOption;
import com.webox.dish.DishOptionGroup;
import com.webox.dish.ProteinType;
import com.webox.dish.SelectionType;
import com.webox.dish.SpiceLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Full dish record for the Console table and edit form. */
public record DishAdminView(
        Long id,
        String name,
        String description,
        BigDecimal price,
        DishCategory category,
        SpiceLevel spiceLevel,
        String imageUrl,
        boolean available,
        Set<Allergen> allergens,
        Set<ProteinType> proteins,
        Instant updatedAt,
        List<OptionGroupView> optionGroups) {

    public record OptionGroupView(Long id, String name, boolean required, SelectionType selectionType,
                                  List<OptionView> options) {
    }

    public record OptionView(Long id, String name, BigDecimal priceDelta, boolean available) {
    }

    public static DishAdminView from(Dish dish) {
        List<OptionGroupView> groups = dish.getOptionGroups().stream()
                .map(group -> new OptionGroupView(group.getId(), group.getName(), group.isRequired(),
                        group.getSelectionType(),
                        group.getOptions().stream()
                                .map(option -> new OptionView(option.getId(), option.getName(),
                                        option.getPriceDelta(), option.isAvailable()))
                                .toList()))
                .toList();
        return new DishAdminView(dish.getId(), dish.getName(), dish.getDescription(), dish.getPrice(),
                dish.getCategory(), dish.getSpiceLevel(), dish.getImageUrl(), dish.isAvailable(),
                new LinkedHashSet<>(dish.getAllergens()), new LinkedHashSet<>(dish.getProteins()),
                dish.getUpdatedAt(), groups);
    }

    /** Convenience for mapping a managed group/option pair without touching the entity graph. */
    static OptionGroupView of(DishOptionGroup group) {
        return new OptionGroupView(group.getId(), group.getName(), group.isRequired(),
                group.getSelectionType(),
                group.getOptions().stream().map(DishAdminView::of).toList());
    }

    static OptionView of(DishOption option) {
        return new OptionView(option.getId(), option.getName(), option.getPriceDelta(), option.isAvailable());
    }
}
