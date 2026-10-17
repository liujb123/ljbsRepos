package com.webox.admin;

import com.webox.common.ApiResponse;
import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.common.PageResponse;
import com.webox.order.Order;
import com.webox.order.OrderRepository;
import com.webox.order.OrderService;
import com.webox.order.OrderStatus;
import com.webox.order.dto.OrderResponse;
import com.webox.order.dto.OrderSummaryResponse;
import com.webox.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal admin order handling.
 *
 * <p>PRD 1 describes the administrator as someone who manages dishes <i>and processes orders</i>,
 * while 4.3 only details dish management for Tier 2. This is the smallest addition that honours
 * that description, and it can be dropped without affecting Tier 1 or Tier 2 completeness.
 */
@RestController
@RequestMapping("/api/admin/orders")
@Validated
@Tag(name = "Console - orders (supplement)")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public AdminOrderController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Order list filtered by meal date and/or status")
    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders;
        if (date != null && status != null) {
            orders = orderRepository.findByMealDateAndStatus(date, status, pageable);
        } else if (date != null) {
            orders = orderRepository.findByMealDate(date, pageable);
        } else if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return ApiResponse.ok(PageResponse.of(orders.map(OrderSummaryResponse::from)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an order Pending -> Confirmed -> Completed")
    public ApiResponse<OrderResponse> updateStatus(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable Long id,
                                                   @RequestBody StatusUpdateRequest request) {
        if (request.targetStatus() == OrderStatus.Cancelled) {
            // Cancellation keeps the PRD 3.4 rule: only pending orders may be cancelled.
            return ApiResponse.ok(orderService.cancelOrder(ownerOf(id), id));
        }
        return ApiResponse.ok(orderService.updateStatus(principal.userId(), id, request.targetStatus()));
    }

    private Long ownerOf(Long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public record StatusUpdateRequest(OrderStatus targetStatus) {
    }
}
