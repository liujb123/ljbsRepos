package com.webox.order;

import com.webox.common.BaseAuditEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An order (PRD 3.4).
 *
 * <p>Two database constraints carry the correctness of this table and are deliberately
 * NOT modelled here, because the column is computed by MySQL:
 * <ul>
 *   <li>{@code uk_orders_user_client_request (user_id, client_request_id)} - idempotent submit.</li>
 *   <li>{@code uk_orders_user_active_slot (user_id, active_slot_key)} - at most one active
 *       (Pending/Confirmed) order per user per meal; {@code active_slot_key} is a STORED
 *       generated column that becomes NULL once the order is cancelled or completed.</li>
 * </ul>
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 32, unique = true)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.Pending;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false, length = 10)
    private MealSlot mealSlot;

    /** What the employee asked for, kept so the auto-switch can be explained after the fact. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_meal_slot", nullable = false, length = 10)
    private MealSlot requestedMealSlot;

    @Column(name = "slot_adjusted", nullable = false)
    private boolean slotAdjusted;

    @Column(name = "delivery_address", nullable = false, length = 200)
    private String deliveryAddress;

    @Column(name = "note", length = 200)
    private String note;

    /** Total portions (sum of line quantities), not the number of distinct dishes. */
    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** Client-generated idempotency key, replayed on retry; unique per user. */
    @Column(name = "client_request_id", nullable = false, length = 64)
    private String clientRequestId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public boolean isCancellable() {
        return status == OrderStatus.Pending;
    }
}
