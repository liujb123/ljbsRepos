package com.webox.order.dto;

import com.webox.order.MealSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;

/**
 * Pre-flight validation of a cart, used when the checkout page opens.
 *
 * <p>Resolving the effective meal and checking availability here means problems surface on
 * page entry instead of after the employee presses "Place Order".
 */
public record CheckoutPreviewRequest(

        @NotEmpty(message = "Your cart is empty.")
        @Valid
        List<OrderLineRequest> items,

        LocalDate deliveryDate,

        MealSlot mealSlot) {
}
