package com.webox.user;

import com.webox.converter.AllergenConverter;
import com.webox.converter.DishCategoryConverter;
import com.webox.converter.SpiceLevelConverter;
import com.webox.converter.TastePreferenceConverter;
import com.webox.dish.Allergen;
import com.webox.dish.DishCategory;
import com.webox.dish.SpiceLevel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Dietary preferences (PRD 4.1). One row per user, created lazily on first save.
 *
 * <p>Which preference drives what:
 * <ul>
 *   <li>{@code allergens} - warns on add-to-cart, never hides a dish.</li>
 *   <li>{@code preferredCategories} / {@code spicePreference} - sort and highlight the menu
 *       when "Recommend for me" is on.</li>
 *   <li>{@code tastePreference} - stored for AI recommendations only; never reorders the menu.</li>
 *   <li>{@code budgetMin} / {@code budgetMax} - non-blocking warning at checkout.</li>
 * </ul>
 */
@Entity
@Table(name = "user_preference")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Convert(converter = SpiceLevelConverter.class)
    @Column(name = "spice_preference", length = 20)
    private SpiceLevel spicePreference;

    @Convert(converter = TastePreferenceConverter.class)
    @Column(name = "taste_preference", length = 20)
    private TastePreference tastePreference;

    @Column(name = "budget_min", precision = 10, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", precision = 10, scale = 2)
    private BigDecimal budgetMax;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_allergen", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "allergen", length = 20, nullable = false)
    @Convert(converter = AllergenConverter.class)
    @BatchSize(size = 100)
    private Set<Allergen> allergens = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_preferred_category", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "category", length = 30, nullable = false)
    @Convert(converter = DishCategoryConverter.class)
    @BatchSize(size = 100)
    private Set<DishCategory> preferredCategories = new LinkedHashSet<>();

    public UserPreference(Long userId) {
        this.userId = userId;
    }
}
