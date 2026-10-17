package com.webox.order.dto;

import com.webox.order.MealSlot;
import com.webox.order.MealSlotResolution;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything the checkout page needs in one round trip (PRD 3.4 / 4.1 / 4.2). */
public record CheckoutPreviewResponse(
        List<LocalDate> bookableDates,
        ResolvedMeal resolved,
        List<PricedLine> pricedItems,
        BigDecimal total,
        List<UnavailableLine> unavailableItems,
        ExistingOrder existingOrder,
        Budget budget) {

    /** The effective meal after the cut-off rules, plus why it differs from the request. */
    public record ResolvedMeal(LocalDate mealDate, MealSlot mealSlot, boolean adjusted,
                               MealSlotResolution.Reason reasonCode) {
    }

    /** Server-computed pricing. Any amount the client sent is ignored. */
    public record PricedLine(String lineKey, Long dishId, String name, BigDecimal unitPrice,
                             int quantity, BigDecimal subtotal, List<String> optionLabels) {
    }

    /** A cart line that cannot be ordered for the resolved meal. */
    public record UnavailableLine(String lineKey, Long dishId, String name, String reason) {
    }

    /** Signals that "Place Order" must become "View Existing Order" (PRD 4.2). */
    public record ExistingOrder(Long id, String orderNo, String status) {
    }

    /** Non-blocking budget feedback (PRD 4.1). */
    public record Budget(BigDecimal min, BigDecimal max, BigDecimal cartTotal, boolean exceedsMax) {
    }
}
