package com.webox.order;

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

/** Snapshot of one selected option on an ordered line (for example "Whole Wheat Bun"). */
@Entity
@Table(name = "order_item_option")
@Getter
@Setter
@NoArgsConstructor
public class OrderItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "group_name", nullable = false, length = 60)
    private String groupName;

    @Column(name = "option_name", nullable = false, length = 60)
    private String optionName;

    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public OrderItemOption(String groupName, String optionName, BigDecimal priceDelta) {
        this.groupName = groupName;
        this.optionName = optionName;
        this.priceDelta = priceDelta == null ? BigDecimal.ZERO : priceDelta;
    }
}
