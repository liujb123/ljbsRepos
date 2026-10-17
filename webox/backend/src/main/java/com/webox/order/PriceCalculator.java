package com.webox.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * All money arithmetic (PRD 6: prices are exact to the cent, floating point must not be used).
 *
 * <p>Every operation normalizes to scale 2 with {@link RoundingMode#HALF_UP}, and the caller
 * only ever sees {@link BigDecimal}. Keeping this in one place means the rules can be proven
 * by unit test rather than by reading every service.
 */
public final class PriceCalculator {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PriceCalculator() {
    }

    /** Normalizes any monetary value to two decimal places. */
    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return value.setScale(SCALE, ROUNDING);
    }

    /** Sums monetary values, returning {@code 0.00} for an empty collection. */
    public static BigDecimal sum(Collection<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        if (values != null) {
            for (BigDecimal value : values) {
                if (value != null) {
                    total = total.add(value);
                }
            }
        }
        return money(total);
    }

    /** {@code base price + the surcharge of every selected option}. */
    public static BigDecimal unitPrice(BigDecimal basePrice, Collection<BigDecimal> optionDeltas) {
        return money(money(basePrice).add(sum(optionDeltas)));
    }

    /** {@code unit price x quantity}. */
    public static BigDecimal subtotal(BigDecimal unitPrice, int quantity) {
        return money(money(unitPrice).multiply(BigDecimal.valueOf(quantity)));
    }

    /** Order total, i.e. the sum of every line subtotal. */
    public static BigDecimal orderTotal(Collection<BigDecimal> subtotals) {
        return sum(subtotals);
    }

    /** True when {@code total} exceeds {@code budgetMax}; used for the non-blocking warning. */
    public static boolean exceedsBudget(BigDecimal total, BigDecimal budgetMax) {
        return budgetMax != null && total != null && money(total).compareTo(money(budgetMax)) > 0;
    }
}
