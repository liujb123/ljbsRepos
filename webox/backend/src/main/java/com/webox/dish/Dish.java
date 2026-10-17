package com.webox.dish;

import com.webox.common.BaseAuditEntity;
import com.webox.converter.AllergenConverter;
import com.webox.converter.DishCategoryConverter;
import com.webox.converter.ProteinTypeConverter;
import com.webox.converter.SpiceLevelConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Dish master data (PRD 3.2 / 6).
 *
 * <p>A dish is only visible to employees when it is both on the requested date's daily
 * menu and {@code available = true}.
 */
@Entity
@Table(name = "dish")
@Getter
@Setter
@NoArgsConstructor
public class Dish extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    /** Always scale 2. Never a floating point type anywhere in the money path. */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Convert(converter = DishCategoryConverter.class)
    @Column(name = "category", nullable = false, length = 30)
    private DishCategory category;

    @Convert(converter = SpiceLevelConverter.class)
    @Column(name = "spice_level", nullable = false, length = 20)
    private SpiceLevel spiceLevel = SpiceLevel.NONE;

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    /** Off-shelf dishes are invisible to employees (PRD 4.3). */
    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "dish_protein", joinColumns = @JoinColumn(name = "dish_id"))
    @Column(name = "protein", length = 20, nullable = false)
    @Convert(converter = ProteinTypeConverter.class)
    @BatchSize(size = 100)
    private Set<ProteinType> proteins = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "dish_allergen", joinColumns = @JoinColumn(name = "dish_id"))
    @Column(name = "allergen", length = 20, nullable = false)
    @Convert(converter = AllergenConverter.class)
    @BatchSize(size = 100)
    private Set<Allergen> allergens = new LinkedHashSet<>();

    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    @BatchSize(size = 100)
    private List<DishOptionGroup> optionGroups = new ArrayList<>();

    public void addOptionGroup(DishOptionGroup group) {
        group.setDish(this);
        group.setDisplayOrder(optionGroups.size());
        this.optionGroups.add(group);
    }
}
