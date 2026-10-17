package com.webox.order.dto;

import com.webox.order.MealSlot;
import com.webox.order.Order;
import com.webox.order.OrderItem;
import com.webox.order.OrderItemOption;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Full order view, backed by the immutable snapshot stored on the order lines. */
public record OrderResponse(
        Long id,
        String orderNo,
        String status,
        LocalDate mealDate,
        MealSlot mealSlot,
        MealSlot requestedMealSlot,
        boolean slotAdjusted,
        String deliveryAddress,
        String note,
        int itemCount,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant cancelledAt,
        boolean duplicated,
        List<OrderLine> items) {

    public record OrderLine(
            Long id,
            Long dishId,
            String dishName,
            String imageUrl,
            BigDecimal unitBasePrice,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal,
            List<SelectedOption> options) {
    }

    public record SelectedOption(String groupName, String optionName, BigDecimal priceDelta) {
    }

    public static OrderResponse from(Order order) {
        return from(order, false);
    }

    public static OrderResponse from(Order order, boolean duplicated) {
        List<OrderLine> lines = order.getItems().stream()
                .map(OrderResponse::toLine)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus().name(),
                order.getMealDate(),
                order.getMealSlot(),
                order.getRequestedMealSlot(),
                order.isSlotAdjusted(),
                order.getDeliveryAddress(),
                order.getNote(),
                order.getItemCount(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getCancelledAt(),
                duplicated,
                lines);
    }

    private static OrderLine toLine(OrderItem item) {
        List<SelectedOption> options = item.getOptions().stream()
                .map(OrderResponse::toOption)
                .toList();
        return new OrderLine(item.getId(), item.getDishId(), item.getDishName(), item.getImageUrl(),
                item.getUnitBasePrice(), item.getUnitPrice(), item.getQuantity(), item.getSubtotal(), options);
    }

    private static SelectedOption toOption(OrderItemOption option) {
        return new SelectedOption(option.getGroupName(), option.getOptionName(), option.getPriceDelta());
    }
}
