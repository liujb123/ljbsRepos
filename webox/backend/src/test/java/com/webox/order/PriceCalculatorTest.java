package com.webox.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PRD 6: prices are exact to the cent and money must never go through a floating point type.
 */
class PriceCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("money is normalized to two decimal places with HALF_UP")
    void normalizesScale() {
        assertThat(PriceCalculator.money(bd("22.5"))).isEqualByComparingTo("22.50");
        assertThat(PriceCalculator.money(bd("22.005"))).isEqualByComparingTo("22.01");
        assertThat(PriceCalculator.money(bd("22.004"))).isEqualByComparingTo("22.00");
        assertThat(PriceCalculator.money(null)).isEqualByComparingTo("0.00");
        assertThat(PriceCalculator.money(bd("22.5")).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("the classic 0.1 + 0.2 case stays exact")
    void decimalAdditionIsExact() {
        BigDecimal sum = PriceCalculator.sum(List.of(bd("0.10"), bd("0.20")));

        assertThat(sum).isEqualByComparingTo("0.30");
        assertThat(sum.toPlainString()).isEqualTo("0.30");
    }

    @Test
    @DisplayName("repeated small surcharges accumulate without drift")
    void repeatedSurchargesDoNotDrift() {
        List<BigDecimal> deltas = List.of(
                bd("0.10"), bd("0.10"), bd("0.10"), bd("0.10"), bd("0.10"),
                bd("0.10"), bd("0.10"), bd("0.10"), bd("0.10"), bd("0.10"));

        assertThat(PriceCalculator.sum(deltas).toPlainString()).isEqualTo("1.00");
    }

    @Test
    @DisplayName("unit price is the base price plus every selected surcharge")
    void unitPriceAddsSurcharges() {
        BigDecimal unitPrice = PriceCalculator.unitPrice(bd("26.50"), List.of(bd("5.00"), bd("3.00")));

        assertThat(unitPrice).isEqualByComparingTo("34.50");
    }

    @Test
    @DisplayName("unit price with no surcharges equals the base price, scaled")
    void unitPriceWithoutSurcharges() {
        assertThat(PriceCalculator.unitPrice(bd("22"), List.of())).isEqualByComparingTo("22.00");
        assertThat(PriceCalculator.unitPrice(bd("22"), null)).isEqualByComparingTo("22.00");
    }

    @Test
    @DisplayName("subtotal multiplies the unit price by the quantity")
    void subtotalMultiplies() {
        assertThat(PriceCalculator.subtotal(bd("22.50"), 3)).isEqualByComparingTo("67.50");
        assertThat(PriceCalculator.subtotal(bd("0.01"), 3)).isEqualByComparingTo("0.03");
    }

    @Test
    @DisplayName("order total is the sum of the line subtotals")
    void orderTotalSumsSubtotals() {
        BigDecimal total = PriceCalculator.orderTotal(List.of(bd("67.50"), bd("34.50"), bd("18.00")));

        assertThat(total).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("a whole order reproduces a hand-computed amount exactly")
    void endToEndAmounts() {
        // 2 x Classic Beef Burger (38.00) with Cheese (+3.00) and Bacon (+5.00)
        BigDecimal burgerUnit = PriceCalculator.unitPrice(bd("38.00"), List.of(bd("3.00"), bd("5.00")));
        BigDecimal burgerSubtotal = PriceCalculator.subtotal(burgerUnit, 2);
        // 1 x Tomato Pasta (26.50) with Cheese (+3.00)
        BigDecimal pastaUnit = PriceCalculator.unitPrice(bd("26.50"), List.of(bd("3.00")));
        BigDecimal pastaSubtotal = PriceCalculator.subtotal(pastaUnit, 1);

        assertThat(burgerUnit).isEqualByComparingTo("46.00");
        assertThat(burgerSubtotal).isEqualByComparingTo("92.00");
        assertThat(pastaUnit).isEqualByComparingTo("29.50");
        assertThat(PriceCalculator.orderTotal(List.of(burgerSubtotal, pastaSubtotal)))
                .isEqualByComparingTo("121.50");
    }

    @Test
    @DisplayName("budget comparison is inclusive of the limit")
    void budgetComparison() {
        assertThat(PriceCalculator.exceedsBudget(bd("30.00"), bd("30.00"))).isFalse();
        assertThat(PriceCalculator.exceedsBudget(bd("30.01"), bd("30.00"))).isTrue();
        assertThat(PriceCalculator.exceedsBudget(bd("30.00"), null)).isFalse();
    }

    @Test
    @DisplayName("empty and null collections are treated as zero")
    void emptyCollections() {
        assertThat(PriceCalculator.sum(List.of())).isEqualByComparingTo("0.00");
        assertThat(PriceCalculator.sum(null)).isEqualByComparingTo("0.00");
        assertThat(PriceCalculator.orderTotal(List.of())).isEqualByComparingTo("0.00");
    }
}
