package com.webox.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One dish scheduled on one date.
 *
 * <p>{@code totalQty} / {@code soldQty} are required by Tier 2 (the admin sets a daily
 * supply quantity) and are reused verbatim by the Tier 3 stock deduction, so enabling
 * real-time stock needs no schema change.
 */
@Entity
@Table(name = "daily_menu_item")
@Getter
@Setter
@NoArgsConstructor
public class DailyMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_menu_id", nullable = false)
    private DailyMenu dailyMenu;

    /** Plain FK id: the menu snapshot loads all referenced dishes in a single batch query. */
    @Column(name = "dish_id", nullable = false)
    private Long dishId;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "sold_qty", nullable = false)
    private int soldQty;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public DailyMenuItem(Long dishId, int totalQty) {
        this.dishId = dishId;
        this.totalQty = totalQty;
    }
}
