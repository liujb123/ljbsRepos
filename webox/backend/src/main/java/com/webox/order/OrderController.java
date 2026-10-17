package com.webox.order;

import com.webox.common.ApiResponse;
import com.webox.common.PageResponse;
import com.webox.order.dto.CheckoutPreviewRequest;
import com.webox.order.dto.CheckoutPreviewResponse;
import com.webox.order.dto.OrderResponse;
import com.webox.order.dto.OrderSummaryResponse;
import com.webox.order.dto.PlaceOrderRequest;
import com.webox.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout-preview")
    @Operation(summary = "Price and validate a cart before submitting it")
    public ApiResponse<CheckoutPreviewResponse> preview(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody CheckoutPreviewRequest request) {
        return ApiResponse.ok(orderService.preview(principal.userId(), request));
    }

    /**
     * Submits an order.
     *
     * <p>{@code Idempotency-Key} is mandatory: the checkout page generates it once on mount and
     * reuses it for every retry, so pressing the button repeatedly can only ever create one order.
     */
    @PostMapping
    @Operation(summary = "Place an order (requires the Idempotency-Key header)")
    public ApiResponse<OrderResponse> placeOrder(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "Client-generated key, stable across retries of the same submit")
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        return ApiResponse.ok(orderService.placeOrder(principal.userId(), idempotencyKey, request));
    }

    @GetMapping
    @Operation(summary = "My orders, newest first")
    public ApiResponse<PageResponse<OrderSummaryResponse>> listOrders(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(orderService.listOrders(principal.userId(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Order detail including the option snapshot")
    public ApiResponse<OrderResponse> getOrder(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrder(principal.userId(), id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending order")
    public ApiResponse<OrderResponse> cancelOrder(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable Long id) {
        return ApiResponse.ok(orderService.cancelOrder(principal.userId(), id));
    }
}
