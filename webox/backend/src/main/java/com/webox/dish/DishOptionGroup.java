package com.webox.dish;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customization group on a dish (PRD 3.2), for example "Bun (required)" or "Add-ons".
 */
@Entity
@Table(name = "dish_option_group")
@Getter
@Setter
@NoArgsConstructor
public class DishOptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dish_id", nullable = false)
    private Dish dish;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** When true the employee must make a selection before the dish can be added. */
    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 10)
    private SelectionType selectionType = SelectionType.SINGLE;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private List<DishOption> options = new ArrayList<>();

    public DishOptionGroup(String name, boolean required, SelectionType selectionType) {
        this.name = name;
        this.required = required;
        this.selectionType = selectionType;
    }

    public void addOption(DishOption option) {
        option.setGroup(this);
        option.setDisplayOrder(options.size());
        this.options.add(option);
    }
}
