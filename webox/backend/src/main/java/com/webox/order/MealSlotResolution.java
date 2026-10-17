package com.webox.order;

import java.time.LocalDate;

/**
 * Outcome of applying the ordering cut-off rules (PRD 4.2).
 *
 * @param mealDate   the meal the order will actually be placed for
 * @param mealSlot   the meal period the order will actually be placed for
 * @param adjusted   true when this differs from what the employee asked for
 * @param reasonCode why it was adjusted, {@code null} when it was not
 */
public record MealSlotResolution(LocalDate mealDate, MealSlot mealSlot, boolean adjusted, Reason reasonCode) {

    /** Maps to an English notice shown on the checkout page. */
    public enum Reason {
        /** Lunch was requested after 10:00 - moved to the same day's dinner. */
        LUNCH_CUTOFF_PASSED,
        /** Dinner was requested after 15:00 - moved to the next day's lunch. */
        DINNER_CUTOFF_PASSED
    }

    public static MealSlotResolution unchanged(LocalDate date, MealSlot slot) {
        return new MealSlotResolution(date, slot, false, null);
    }
}
