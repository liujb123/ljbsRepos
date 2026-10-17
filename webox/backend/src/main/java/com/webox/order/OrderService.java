package com.webox.order;

import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.common.PageResponse;
import com.webox.config.WeBoxProperties;
import com.webox.dish.Dish;
import com.webox.dish.DishRepository;
import com.webox.menu.DailyMenu;
import com.webox.menu.DailyMenuRepository;
import com.webox.menu.DailyMenuItem;
import com.webox.order.CartRuleValidator.Selection;
import com.webox.order.dto.CheckoutPreviewRequest;
import com.webox.order.dto.CheckoutPreviewResponse;
import com.webox.order.dto.OrderLineRequest;
import com.webox.order.dto.OrderResponse;
import com.webox.order.dto.OrderSummaryResponse;
import com.webox.order.dto.PlaceOrderRequest;
import com.webox.user.UserPreference;
import com.webox.user.UserPreferenceRepository;
import com.webox.user.UserService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ordering use cases (PRD 3.4 and 4.2).
 *
 * <p><b>Correctness model.</b> Application checks exist to produce good English messages; they
 * are not what makes the rules hold under concurrency. Two unique indexes do that:
 * <ul>
 *   <li>{@code uk_orders_user_client_request} - one order per Idempotency-Key.</li>
 *   <li>{@code uk_orders_user_active_slot} - one active order per user per meal.</li>
 * </ul>
 * Consequently this class never needs a distributed lock, and stays correct when Redis is down
 * or when several instances run behind a load balancer.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final String CONSTRAINT_IDEMPOTENCY = "uk_orders_user_client_request";
    private static final String CONSTRAINT_ACTIVE_SLOT = "uk_orders_user_active_slot";
    /** How long a duplicate submit waits for the in-flight original to commit. */
    private static final Duration IN_FLIGHT_WAIT = Duration.ofSeconds(3);
    private static final long IN_FLIGHT_POLL_MILLIS = 40L;

    private final OrderRepository orderRepository;
    private final OrderStatusLogRepository statusLogRepository;
    private final DishRepository dishRepository;
    private final DailyMenuRepository dailyMenuRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final MealSlotResolver mealSlotResolver;
    private final IdempotencyService idempotencyService;
    private final OrderNoGenerator orderNoGenerator;
    private final UserService userService;
    private final TransactionTemplate transactionTemplate;
    private final WeBoxProperties properties;

    public OrderService(OrderRepository orderRepository,
                        OrderStatusLogRepository statusLogRepository,
                        DishRepository dishRepository,
                        DailyMenuRepository dailyMenuRepository,
                        UserPreferenceRepository preferenceRepository,
                        MealSlotResolver mealSlotResolver,
                        IdempotencyService idempotencyService,
                        OrderNoGenerator orderNoGenerator,
                        UserService userService,
                        TransactionTemplate transactionTemplate,
                        WeBoxProperties properties) {
        this.orderRepository = orderRepository;
        this.statusLogRepository = statusLogRepository;
        this.dishRepository = dishRepository;
        this.dailyMenuRepository = dailyMenuRepository;
        this.preferenceRepository = preferenceRepository;
        this.mealSlotResolver = mealSlotResolver;
        this.idempotencyService = idempotencyService;
        this.orderNoGenerator = orderNoGenerator;
        this.userService = userService;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Checkout preview
    // ------------------------------------------------------------------

    /** Read-only pre-flight for the checkout page. Never creates anything. */
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(Long userId, CheckoutPreviewRequest request) {
        MealSlotResolution resolution = request.mealSlot() == null
                ? mealSlotResolver.nearestBookable()
                : mealSlotResolver.resolve(
                        request.deliveryDate() == null ? mealSlotResolver.today() : request.deliveryDate(),
                        request.mealSlot());

        Set<Long> menuDishIds = dishIdsOnMenu(resolution.mealDate());
        Map<Long, Dish> dishes = loadDishes(request.items());

        List<CheckoutPreviewResponse.PricedLine> priced = new ArrayList<>();
        List<CheckoutPreviewResponse.UnavailableLine> unavailable = new ArrayList<>();
        List<BigDecimal> subtotals = new ArrayList<>();

        for (OrderLineRequest line : request.items()) {
            Dish dish = dishes.get(line.dishId());
            String lineKey = line.lineKey();
            if (dish == null) {
                unavailable.add(new CheckoutPreviewResponse.UnavailableLine(
                        lineKey, line.dishId(), null, ErrorCode.DISH_NOT_FOUND.name()));
                continue;
            }
            if (!dish.isAvailable()) {
                unavailable.add(new CheckoutPreviewResponse.UnavailableLine(
                        lineKey, dish.getId(), dish.getName(), ErrorCode.DISH_UNAVAILABLE.name()));
                continue;
            }
            if (!menuDishIds.contains(dish.getId())) {
                unavailable.add(new CheckoutPreviewResponse.UnavailableLine(
                        lineKey, dish.getId(), dish.getName(), ErrorCode.DISH_NOT_ON_MENU.name()));
                continue;
            }
            List<Selection> selections;
            try {
                selections = CartRuleValidator.resolveSelections(dish, line.optionIdsOrEmpty());
            } catch (BusinessException ex) {
                unavailable.add(new CheckoutPreviewResponse.UnavailableLine(
                        lineKey, dish.getId(), dish.getName(), ErrorCode.OPTION_INVALID.name()));
                continue;
            }

            BigDecimal unitPrice = PriceCalculator.unitPrice(dish.getPrice(),
                    selections.stream().map(Selection::priceDelta).toList());
            BigDecimal subtotal = PriceCalculator.subtotal(unitPrice, line.quantity());
            subtotals.add(subtotal);
            priced.add(new CheckoutPreviewResponse.PricedLine(
                    lineKey, dish.getId(), dish.getName(), unitPrice, line.quantity(), subtotal,
                    selections.stream().map(Selection::optionName).toList()));
        }

        BigDecimal total = PriceCalculator.orderTotal(subtotals);

        CheckoutPreviewResponse.ExistingOrder existingOrder =
                findActiveOrder(userId, resolution.mealDate(), resolution.mealSlot())
                        .map(order -> new CheckoutPreviewResponse.ExistingOrder(
                                order.getId(), order.getOrderNo(), order.getStatus().name()))
                        .orElse(null);

        return new CheckoutPreviewResponse(
                bookableDates(),
                new CheckoutPreviewResponse.ResolvedMeal(resolution.mealDate(), resolution.mealSlot(),
                        resolution.adjusted(), resolution.reasonCode()),
                priced,
                total,
                unavailable,
                existingOrder,
                buildBudget(userId, total));
    }

    // ------------------------------------------------------------------
    // Place order
    // ------------------------------------------------------------------

    /**
     * Submits an order exactly once per {@code Idempotency-Key}.
     *
     * <p>The transaction boundary is explicit so that a constraint violation can be handled
     * <i>after</i> the failed transaction has rolled back, where the existing order can safely
     * be read back and returned.
     */
    public OrderResponse placeOrder(Long userId, String idempotencyKey, PlaceOrderRequest request) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (key.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must be 64 characters or fewer.");
        }

        if (!idempotencyService.tryAcquire(userId, key)) {
            // Someone already owns this key: either a completed submit (replay) or a duplicate
            // that arrived while the first request is still in its transaction. Wait briefly for
            // the order to become visible so a genuine double-click returns the same order
            // instead of an error.
            Order replay = awaitExistingOrder(userId, key);
            if (replay != null) {
                return readResponse(replay.getId(), true);
            }
            throw new BusinessException(ErrorCode.ORDER_IN_PROGRESS);
        }

        try {
            // The response is assembled inside the transaction: the order's lines and their
            // option snapshots are lazy collections and must not be touched after commit.
            return transactionTemplate.execute(status ->
                    OrderResponse.from(doPlaceOrder(userId, key, request), false));
        } catch (DataIntegrityViolationException ex) {
            return handleConstraintViolation(userId, key, ex);
        } catch (RuntimeException ex) {
            // A genuine failure: free the key so the employee can correct and retry.
            idempotencyService.release(userId, key);
            throw ex;
        }
    }

    private Order doPlaceOrder(Long userId, String clientRequestId, PlaceOrderRequest request) {
        MealSlotResolution resolution =
                mealSlotResolver.resolve(request.deliveryDate(), request.mealSlot());

        Set<Long> menuDishIds = dishIdsOnMenu(resolution.mealDate());
        Map<Long, Dish> dishes = loadDishes(request.items());

        List<ResolvedLine> lines = new ArrayList<>();
        int totalQuantity = 0;
        List<BigDecimal> subtotals = new ArrayList<>();

        for (OrderLineRequest line : request.items()) {
            Dish dish = dishes.get(line.dishId());
            if (dish == null) {
                throw new BusinessException(ErrorCode.DISH_NOT_FOUND);
            }
            if (!dish.isAvailable()) {
                throw new BusinessException(ErrorCode.DISH_UNAVAILABLE,
                        "\"" + dish.getName() + "\" is currently unavailable.");
            }
            if (!menuDishIds.contains(dish.getId())) {
                throw new BusinessException(ErrorCode.DISH_NOT_ON_MENU,
                        "\"" + dish.getName() + "\" is not available for this meal.");
            }

            List<Selection> selections = CartRuleValidator.resolveSelections(dish, line.optionIdsOrEmpty());
            // Prices always come from the database, never from the request.
            BigDecimal unitPrice = PriceCalculator.unitPrice(dish.getPrice(),
                    selections.stream().map(Selection::priceDelta).toList());
            BigDecimal subtotal = PriceCalculator.subtotal(unitPrice, line.quantity());

            totalQuantity += line.quantity();
            subtotals.add(subtotal);
            lines.add(new ResolvedLine(dish, line.quantity(), selections, unitPrice, subtotal));
        }

        CartRuleValidator.validateItemCount(totalQuantity, properties.order().maxItemsPerOrder());

        // Friendly pre-check. Racing requests may both pass here; the unique index decides.
        findActiveOrder(userId, resolution.mealDate(), resolution.mealSlot())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.ORDER_SLOT_CONFLICT, ErrorCode.ORDER_SLOT_CONFLICT.message(),
                            Map.of("existingOrderId", existing.getId(), "orderNo", existing.getOrderNo()));
                });

        Order order = new Order();
        order.setOrderNo(orderNoGenerator.next(resolution.mealDate()));
        order.setUserId(userId);
        order.setStatus(OrderStatus.Pending);
        order.setMealDate(resolution.mealDate());
        order.setMealSlot(resolution.mealSlot());
        order.setRequestedMealSlot(request.mealSlot());
        order.setSlotAdjusted(resolution.adjusted());
        order.setDeliveryAddress(request.deliveryAddress().trim());
        order.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        order.setItemCount(totalQuantity);
        order.setTotalAmount(PriceCalculator.orderTotal(subtotals));
        order.setClientRequestId(clientRequestId);

        for (ResolvedLine line : lines) {
            OrderItem item = new OrderItem();
            item.setDishId(line.dish().getId());
            item.setDishName(line.dish().getName());
            item.setImageUrl(line.dish().getImageUrl());
            item.setUnitBasePrice(PriceCalculator.money(line.dish().getPrice()));
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setSubtotal(line.subtotal());
            for (Selection selection : line.selections()) {
                item.addOption(new OrderItemOption(selection.groupName(), selection.optionName(),
                        PriceCalculator.money(selection.priceDelta())));
            }
            order.addItem(item);
        }

        // Flush now so a unique-constraint violation surfaces here, inside the transaction.
        Order saved = orderRepository.saveAndFlush(order);
        statusLogRepository.save(new OrderStatusLog(saved.getId(), null, OrderStatus.Pending, userId));
        return saved;
    }

    /**
     * Polls for an order that another in-flight request with the same key is about to commit.
     *
     * <p>Each lookup runs in its own short transaction, so it only ever sees committed rows.
     * Called only on the duplicate path, and bounded so a stuck request cannot hold a thread.
     */
    private Order awaitExistingOrder(Long userId, String key) {
        long deadline = System.nanoTime() + IN_FLIGHT_WAIT.toNanos();
        while (true) {
            Optional<Order> found = orderRepository.findByUserIdAndClientRequestId(userId, key);
            if (found.isPresent()) {
                return found.get();
            }
            if (System.nanoTime() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(IN_FLIGHT_POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    /**
     * Translates a unique-constraint violation into the correct API outcome.
     * Runs in its own transaction, after the failed one has rolled back.
     */
    private OrderResponse handleConstraintViolation(Long userId, String key, DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        String detail = message == null ? "" : message;

        if (detail.contains(CONSTRAINT_IDEMPOTENCY)) {
            // The same key already produced an order: a true idempotent replay.
            return orderRepository.findByUserIdAndClientRequestId(userId, key)
                    .map(order -> readResponse(order.getId(), true))
                    .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_IN_PROGRESS));
        }

        if (detail.contains(CONSTRAINT_ACTIVE_SLOT)) {
            // A concurrent request won the same meal. Let this one be retried for another meal.
            idempotencyService.release(userId, key);
            throw new BusinessException(ErrorCode.ORDER_SLOT_CONFLICT);
        }

        idempotencyService.release(userId, key);
        log.warn("Unclassified data integrity violation on order submit: {}", detail);
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The request conflicts with existing data.");
    }

    // ------------------------------------------------------------------
    // Queries and cancellation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrders(Long userId, int page, int size) {
        Page<OrderSummaryResponse> result = orderRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
                .map(OrderSummaryResponse::from);
        return PageResponse.of(result);
    }

    /** @throws BusinessException 403 when the order belongs to somebody else. */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, Long orderId) {
        return OrderResponse.from(loadOrderForUser(userId, orderId));
    }

    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = loadOrderForUser(userId, orderId);
        if (order.getStatus() == OrderStatus.Cancelled) {
            // Idempotent: cancelling twice is not an error.
            return OrderResponse.from(order);
        }
        if (!order.isCancellable()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(OrderStatus.Cancelled);
        order.setCancelledAt(Instant.now());
        // Clearing the status also clears active_slot_key, releasing the meal slot immediately.
        Order saved = orderRepository.saveAndFlush(order);
        statusLogRepository.save(new OrderStatusLog(saved.getId(), previous, OrderStatus.Cancelled, userId));
        return OrderResponse.from(saved);
    }

    /** Admin-supplement transitions: Pending -> Confirmed -> Completed. */
    @Transactional
    public OrderResponse updateStatus(Long actorUserId, Long orderId, OrderStatus target) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        OrderStatus current = order.getStatus();
        boolean allowed = (current == OrderStatus.Pending && target == OrderStatus.Confirmed)
                || (current == OrderStatus.Confirmed && target == OrderStatus.Completed);
        if (!allowed) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Order cannot move from " + current + " to " + target + ".");
        }
        order.setStatus(target);
        Order saved = orderRepository.saveAndFlush(order);
        statusLogRepository.save(new OrderStatusLog(saved.getId(), current, target, actorUserId));
        return OrderResponse.from(saved);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private record ResolvedLine(Dish dish, int quantity, List<Selection> selections,
                                BigDecimal unitPrice, BigDecimal subtotal) {
    }

    private Order loadOrderForUser(Long userId, Long orderId) {
        Order order = loadOrderWithLines(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return order;
    }

    private Order loadOrderWithLines(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.getItems().forEach(item -> item.getOptions().size());
        return order;
    }

    /**
     * Builds a response in its own read transaction, so lazy line/option collections can be
     * initialized safely outside the write transaction that just ended.
     */
    private OrderResponse readResponse(Long orderId, boolean duplicated) {
        return transactionTemplate.execute(status ->
                OrderResponse.from(loadOrderWithLines(orderId), duplicated));
    }

    private Optional<Order> findActiveOrder(Long userId, LocalDate mealDate, MealSlot mealSlot) {
        return orderRepository
                .findActiveSlotOrders(userId, mealDate, mealSlot, List.of(OrderStatus.Pending, OrderStatus.Confirmed))
                .stream()
                .findFirst();
    }

    private Set<Long> dishIdsOnMenu(LocalDate date) {
        return dailyMenuRepository.findByMenuDate(date)
                .map(DailyMenu::getItems)
                .map(items -> items.stream().map(DailyMenuItem::getDishId).collect(Collectors.toSet()))
                .orElseGet(Set::of);
    }

    private Map<Long, Dish> loadDishes(List<OrderLineRequest> lines) {
        Set<Long> ids = new HashSet<>();
        for (OrderLineRequest line : lines) {
            if (line.dishId() != null) {
                ids.add(line.dishId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Dish> byId = new HashMap<>();
        dishRepository.findAllById(ids).forEach(dish -> byId.put(dish.getId(), dish));
        // Force the option graph to load inside the transaction, once per dish.
        byId.values().forEach(dish -> dish.getOptionGroups().forEach(group -> group.getOptions().size()));
        return byId;
    }

    private List<LocalDate> bookableDates() {
        LocalDate today = mealSlotResolver.today();
        return dailyMenuRepository.findMenuDatesBetween(today, today.plusDays(properties.order().maxAdvanceDays()));
    }

    private CheckoutPreviewResponse.Budget buildBudget(Long userId, BigDecimal cartTotal) {
        UserPreference preference = preferenceRepository.findById(userId).orElse(null);
        if (preference == null || preference.getBudgetMax() == null) {
            return null;
        }
        return new CheckoutPreviewResponse.Budget(
                preference.getBudgetMin(),
                preference.getBudgetMax(),
                cartTotal,
                PriceCalculator.exceedsBudget(cartTotal, preference.getBudgetMax()));
    }
}
