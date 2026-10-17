package com.webox.dish;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A selectable option inside a {@link DishOptionGroup}, optionally carrying a surcharge. */
@Entity
@Table(name = "dish_option")
@Getter
@Setter
@NoArgsConstructor
public class DishOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private DishOptionGroup group;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Extra cost in CNY, scale 2. Zero for a free choice such as a bun type. */
    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public DishOption(String name, BigDecimal priceDelta) {
        this.name = name;
        this.priceDelta = priceDelta == null ? BigDecimal.ZERO : priceDelta;
    }
}
