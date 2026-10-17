package com.webox.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * One line of the cart being submitted.
 *
 * @param lineKey   opaque client identifier, echoed back so the SPA can match problems to rows
 * @param dishId    the dish being ordered
 * @param quantity  portions of this configuration
 * @param optionIds the chosen {@code dish_option} ids; the server validates them against the dish
 */
public record OrderLineRequest(

        @Size(max = 120, message = "Line key must be 120 characters or fewer.")
        String lineKey,

        @NotNull(message = "A dish is required for every line.")
        Long dishId,

        @Min(value = 1, message = "Quantity must be at least 1.")
        @Max(value = 5, message = "Quantity must be at most 5.")
        int quantity,

        Set<Long> optionIds) {

    public Set<Long> optionIdsOrEmpty() {
        return optionIds == null ? Set.of() : optionIds;
    }

    @Valid
    public OrderLineRequest {
    }
}
