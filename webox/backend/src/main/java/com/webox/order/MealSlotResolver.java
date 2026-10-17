package com.webox.order;

import com.webox.common.BusinessException;
import com.webox.common.ErrorCode;
import com.webox.config.WeBoxProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/**
 * Cut-off and auto-switch rules (PRD 4.2).
 *
 * <pre>
 *   Lunch  must be submitted before 10:00 on the meal date
 *   Dinner must be submitted before 15:00 on the meal date
 *
 *   Once a meal's cut-off has passed, ordering switches to the nearest bookable meal:
 *     - requested Lunch, and today's dinner is still open  -> same day, Dinner
 *     - otherwise                                          -> next day, Lunch
 * </pre>
 *
 * <p>This is a pure function of "now": the {@link Clock} is injected rather than read from
 * {@code LocalDateTime.now()}, so every boundary can be pinned in a unit test.
 *
 * <p>The fallback branch always yields a next-day Lunch whose cut-off is in the future, so the
 * resolution never recurses and never returns empty.
 */
@Component
public class MealSlotResolver {

    public static final LocalTime LUNCH_CUTOFF = LocalTime.of(10, 0);
    public static final LocalTime DINNER_CUTOFF = LocalTime.of(15, 0);

    private final Clock clock;
    private final ZoneId zone;
    private final int maxAdvanceDays;

    public MealSlotResolver(Clock clock, ZoneId businessZone, WeBoxProperties properties) {
        this.clock = clock;
        this.zone = businessZone;
        this.maxAdvanceDays = properties.order().maxAdvanceDays();
    }

    /** Applies the cut-off rules to a concrete request. */
    public MealSlotResolution resolve(LocalDate wantedDate, MealSlot wantedSlot) {
        if (wantedDate == null || wantedSlot == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A delivery date and meal are required.");
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        if (wantedDate.isBefore(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The delivery date cannot be in the past.");
        }
        if (wantedDate.isAfter(today.plusDays(maxAdvanceDays))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The delivery date can be at most " + maxAdvanceDays + " days ahead.");
        }

        if (now.isBefore(cutoff(wantedDate, wantedSlot))) {
            return MealSlotResolution.unchanged(wantedDate, wantedSlot);
        }

        if (wantedSlot == MealSlot.Lunch && now.isBefore(cutoff(wantedDate, MealSlot.Dinner))) {
            return new MealSlotResolution(wantedDate, MealSlot.Dinner, true,
                    MealSlotResolution.Reason.LUNCH_CUTOFF_PASSED);
        }
        return new MealSlotResolution(wantedDate.plusDays(1), MealSlot.Lunch, true,
                MealSlotResolution.Reason.DINNER_CUTOFF_PASSED);
    }

    /** The closest meal that can still be ordered right now - used as the checkout default. */
    public MealSlotResolution nearestBookable() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        if (now.isBefore(cutoff(today, MealSlot.Lunch))) {
            return MealSlotResolution.unchanged(today, MealSlot.Lunch);
        }
        if (now.isBefore(cutoff(today, MealSlot.Dinner))) {
            return MealSlotResolution.unchanged(today, MealSlot.Dinner);
        }
        return MealSlotResolution.unchanged(today.plusDays(1), MealSlot.Lunch);
    }

    /** Whether the given meal can still be ordered, used to disable closed options in the UI. */
    public boolean isBookable(LocalDate date, MealSlot slot) {
        return ZonedDateTime.now(clock).isBefore(cutoff(date, slot));
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private ZonedDateTime cutoff(LocalDate date, MealSlot slot) {
        LocalTime time = slot == MealSlot.Lunch ? LUNCH_CUTOFF : DINNER_CUTOFF;
        return ZonedDateTime.of(date, time, zone);
    }
}
