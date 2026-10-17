package com.webox.admin.dto;

import com.webox.dish.Allergen;
import com.webox.dish.DishCategory;
import com.webox.dish.ProteinType;
import com.webox.dish.SelectionType;
import com.webox.dish.SpiceLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Create/update payload for a dish (PRD 4.3).
 *
 * <p>{@code optionGroups} replaces the dish's whole option graph inside one transaction. Orders
 * already placed are unaffected because their lines are snapshots.
 */
public record DishUpsertRequest(

        @NotBlank(message = "Dish name is required.")
        @Size(max = 100, message = "Dish name must be 100 characters or fewer.")
        String name,

        @NotBlank(message = "Description is required.")
        @Size(max = 500, message = "Description must be 500 characters or fewer.")
        String description,

        @NotNull(message = "Price is required.")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0.")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 2 decimal places.")
        BigDecimal price,

        @NotNull(message = "Category is required.")
        DishCategory category,

        @NotNull(message = "Spice level is required.")
        SpiceLevel spiceLevel,

        @Size(max = 300, message = "Image URL must be 300 characters or fewer.")
        String imageUrl,

        Set<Allergen> allergens,

        Set<ProteinType> proteins,

        Boolean available,

        @Valid
        List<OptionGroupRequest> optionGroups) {

    public record OptionGroupRequest(

            @NotBlank(message = "Option group name is required.")
            @Size(max = 60, message = "Option group name must be 60 characters or fewer.")
            String name,

            boolean required,

            @NotNull(message = "Selection type is required.")
            SelectionType selectionType,

            @NotEmpty(message = "A required group must have at least one option.")
            @Valid
            List<OptionRequest> options) {
    }

    public record OptionRequest(

            @NotBlank(message = "Option name is required.")
            @Size(max = 60, message = "Option name must be 60 characters or fewer.")
            String name,

            @DecimalMin(value = "0.00", message = "Extra price cannot be negative.")
            @Digits(integer = 8, fraction = 2, message = "Extra price must have at most 2 decimal places.")
            BigDecimal priceDelta,

            Boolean available) {
    }
}
