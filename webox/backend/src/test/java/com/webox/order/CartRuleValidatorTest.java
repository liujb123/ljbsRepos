package com.webox.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.dish.Dish;
import com.webox.dish.DishOption;
import com.webox.dish.DishOptionGroup;
import com.webox.dish.SelectionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cart rules from PRD 3.2 and 4.2: option validity and the five-portion cap. */
class CartRuleValidatorTest {

    private Dish burger;

    @BeforeEach
    void setUp() {
        burger = new Dish();
        burger.setId(1L);
        burger.setName("Classic Beef Burger");

        DishOptionGroup bun = new DishOptionGroup("Bun", true, SelectionType.SINGLE);
        bun.addOption(option(10L, "Plain", "0.00"));
        bun.addOption(option(11L, "Whole Wheat", "0.00"));
        burger.addOptionGroup(bun);

        DishOptionGroup sauce = new DishOptionGroup("Sauce", true, SelectionType.SINGLE);
        sauce.addOption(option(20L, "Ketchup", "0.00"));
        sauce.addOption(option(21L, "Mustard", "0.00"));
        burger.addOptionGroup(sauce);

        DishOptionGroup addOns = new DishOptionGroup("Add-ons", false, SelectionType.MULTI);
        addOns.addOption(option(30L, "Cheese", "3.00"));
        addOns.addOption(option(31L, "Bacon", "5.00"));
        burger.addOptionGroup(addOns);

        // A discontinued option must still be rejected even if the client sends its id.
        DishOption retired = option(32L, "Truffle", "9.00");
        retired.setAvailable(false);
        addOns.addOption(retired);
    }

    private static DishOption option(Long id, String name, String priceDelta) {
        DishOption option = new DishOption(name, new BigDecimal(priceDelta));
        option.setId(id);
        return option;
    }

    @Test
    @DisplayName("a valid single-choice selection in every required group resolves")
    void validSelectionResolves() {
        List<CartRuleValidator.Selection> selections =
                CartRuleValidator.resolveSelections(burger, Set.of(10L, 20L));

        assertThat(selections).hasSize(2);
        assertThat(selections).extracting(CartRuleValidator.Selection::optionName)
                .containsExactly("Plain", "Ketchup");
    }

    @Test
    @DisplayName("selections are returned in option-group display order")
    void selectionsFollowGroupOrder() {
        List<CartRuleValidator.Selection> selections =
                CartRuleValidator.resolveSelections(burger, Set.of(20L, 11L, 30L, 31L));

        assertThat(selections).extracting(CartRuleValidator.Selection::groupName)
                .containsExactly("Bun", "Sauce", "Add-ons", "Add-ons");
    }

    @Test
    @DisplayName("multi-choice add-ons carry their surcharge")
    void multiChoiceCarriesSurcharges() {
        List<CartRuleValidator.Selection> selections =
                CartRuleValidator.resolveSelections(burger, Set.of(10L, 20L, 30L, 31L));

        assertThat(selections).extracting(CartRuleValidator.Selection::priceDelta)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("0.00"),
                        new BigDecimal("3.00"), new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("an unsatisfied required group is rejected")
    void missingRequiredGroupRejected() {
        assertThatThrownBy(() -> CartRuleValidator.resolveSelections(burger, Set.of(10L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sauce");
    }

    @Test
    @DisplayName("an optional group may stay empty")
    void optionalGroupMayBeEmpty() {
        assertThat(CartRuleValidator.resolveSelections(burger, Set.of(10L, 20L))).hasSize(2);
    }

    @Test
    @DisplayName("two options in a single-choice group are rejected")
    void twoOptionsInSingleGroupRejected() {
        assertThatThrownBy(() -> CartRuleValidator.resolveSelections(burger, Set.of(10L, 11L, 20L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only one option");
    }

    @Test
    @DisplayName("an option id that does not belong to the dish is rejected")
    void foreignOptionIdRejected() {
        assertThatThrownBy(() -> CartRuleValidator.resolveSelections(burger, Set.of(10L, 20L, 999L)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.OPTION_INVALID);
    }

    @Test
    @DisplayName("a discontinued option is rejected even when its id is sent")
    void discontinuedOptionRejected() {
        assertThatThrownBy(() -> CartRuleValidator.resolveSelections(burger, Set.of(10L, 20L, 32L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("a dish with no options accepts an empty selection")
    void dishWithoutOptions() {
        Dish plain = new Dish();
        plain.setId(2L);

        assertThat(CartRuleValidator.resolveSelections(plain, Set.of())).isEmpty();
        assertThat(CartRuleValidator.resolveSelections(plain, null)).isEmpty();
    }

    @Test
    @DisplayName("five portions is allowed, six is not (3 + 3 = 6, PRD 4.2)")
    void portionCap() {
        CartRuleValidator.validateItemCount(1, 5);
        CartRuleValidator.validateItemCount(5, 5);

        assertThatThrownBy(() -> CartRuleValidator.validateItemCount(6, 5))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ORDER_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("an empty order is rejected")
    void emptyOrderRejected() {
        assertThatThrownBy(() -> CartRuleValidator.validateItemCount(0, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cart is empty");
    }
}
