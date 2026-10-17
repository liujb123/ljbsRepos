package com.webox.order.dto;

import com.webox.order.MealSlot;
import com.webox.order.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Row of the "My Orders" list (PRD 3.4). */
public record OrderSummaryResponse(
        Long id,
        String orderNo,
        String status,
        LocalDate mealDate,
        MealSlot mealSlot,
        int itemCount,
        BigDecimal totalAmount,
        Instant createdAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getOrderNo(), order.getStatus().name(),
                order.getMealDate(), order.getMealSlot(), order.getItemCount(), order.getTotalAmount(),
                order.getCreatedAt());
    }
}
