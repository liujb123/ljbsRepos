package com.webox.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Whole-day replacement of a date's menu (PRD 4.3).
 *
 * <p>Sending the complete list makes the operation idempotent: submitting the same body twice
 * produces the same menu. {@code totalQty} is the daily supply quantity.
 */
public record DailyMenuUpsertRequest(

        @NotEmpty(message = "Select at least one dish for this date.")
        @Valid
        List<Item> items) {

    public record Item(

            @NotNull(message = "A dish is required.")
            Long dishId,

            @Min(value = 0, message = "Daily quantity must be zero or greater.")
            int totalQty) {
    }

    /** Response shape: the saved menu for a date. */
    public record View(LocalDate menuDate, List<ItemView> items) {
    }

    public record ItemView(Long dishId, String name, String category, boolean available,
                           int totalQty, int soldQty, int displayOrder) {
    }
}
