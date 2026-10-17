package com.webox.order.dto;

import com.webox.order.MealSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Order submission payload (PRD 3.4).
 *
 * <p>The client supplies only intent - what, how many, for which meal, delivered where. Prices,
 * the effective meal after the cut-off rules and all rule checks are decided by the server.
 */
public record PlaceOrderRequest(

        @NotEmpty(message = "Your cart is empty.")
        @Valid
        List<OrderLineRequest> items,

        @NotNull(message = "A delivery date is required.")
        LocalDate deliveryDate,

        @NotNull(message = "A meal is required.")
        MealSlot mealSlot,

        @NotBlank(message = "Delivery address is required.")
        @Size(max = 200, message = "Delivery address must be 200 characters or fewer.")
        String deliveryAddress,

        @Size(max = 200, message = "Notes must be 200 characters or fewer.")
        String note) {
}
