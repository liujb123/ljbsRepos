package com.webox.order;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Optional<Order> findByUserIdAndClientRequestId(Long userId, String clientRequestId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * The "one active order per meal" pre-check. This only exists to produce a friendly
     * English message; the authoritative guarantee is uk_orders_user_active_slot.
     */
    @Query("""
            select o from Order o
             where o.userId = :userId
               and o.mealDate = :mealDate
               and o.mealSlot = :mealSlot
               and o.status in :statuses
            """)
    List<Order> findActiveSlotOrders(@Param("userId") Long userId,
                                     @Param("mealDate") LocalDate mealDate,
                                     @Param("mealSlot") MealSlot mealSlot,
                                     @Param("statuses") Collection<OrderStatus> statuses);

    long countByMealDateAndStatus(LocalDate mealDate, OrderStatus status);

    List<Order> findByMealDate(LocalDate mealDate);

    Page<Order> findByMealDate(LocalDate mealDate, Pageable pageable);

    Page<Order> findByMealDateAndStatus(LocalDate mealDate, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
