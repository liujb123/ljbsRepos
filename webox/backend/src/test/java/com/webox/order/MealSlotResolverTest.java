package com.webox.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webox.common.BusinessException;
import com.webox.config.WeBoxProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PRD 4.2 cut-off and auto-switch rules.
 *
 * <p>Every boundary is pinned by injecting a fixed {@link Clock}, which is exactly why the
 * resolver takes one instead of reading the system time.
 */
class MealSlotResolverTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 24);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private static MealSlotResolver resolverAt(String localDateTime) {
        Clock clock = Clock.fixed(LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant(), ZONE);
        WeBoxProperties properties = new WeBoxProperties(
                "Asia/Shanghai", null, null, null, null,
                new WeBoxProperties.Order(5, 24, 7), null);
        return new MealSlotResolver(clock, ZONE, properties);
    }

    @Test
    @DisplayName("before 10:00 lunch is still bookable for today")
    void lunchBeforeCutoff() {
        MealSlotResolution resolution = resolverAt("2026-03-24T09:00:00").resolve(TODAY, MealSlot.Lunch);

        assertThat(resolution.mealDate()).isEqualTo(TODAY);
        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Lunch);
        assertThat(resolution.adjusted()).isFalse();
        assertThat(resolution.reasonCode()).isNull();
    }

    @Test
    @DisplayName("09:59:59 is inside the lunch window")
    void lunchJustBeforeCutoff() {
        MealSlotResolution resolution = resolverAt("2026-03-24T09:59:59").resolve(TODAY, MealSlot.Lunch);

        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Lunch);
        assertThat(resolution.adjusted()).isFalse();
    }

    @Test
    @DisplayName("10:00:00 sharp closes lunch and switches to today's dinner")
    void lunchCutoffIsInclusive() {
        MealSlotResolution resolution = resolverAt("2026-03-24T10:00:00").resolve(TODAY, MealSlot.Lunch);

        assertThat(resolution.mealDate()).isEqualTo(TODAY);
        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Dinner);
        assertThat(resolution.adjusted()).isTrue();
        assertThat(resolution.reasonCode()).isEqualTo(MealSlotResolution.Reason.LUNCH_CUTOFF_PASSED);
    }

    @Test
    @DisplayName("PRD example: lunch submitted at 10:30 becomes today's dinner")
    void prdExampleLunchAfterCutoffBecomesDinner() {
        MealSlotResolution resolution = resolverAt("2026-03-24T10:30:00").resolve(TODAY, MealSlot.Lunch);

        assertThat(resolution.mealDate()).isEqualTo(TODAY);
        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Dinner);
        assertThat(resolution.adjusted()).isTrue();
        assertThat(resolution.reasonCode()).isEqualTo(MealSlotResolution.Reason.LUNCH_CUTOFF_PASSED);
    }

    @Test
    @DisplayName("dinner requested at 10:30 stays on today's dinner, unchanged")
    void dinnerStillOpenAt1030() {
        MealSlotResolution resolution = resolverAt("2026-03-24T10:30:00").resolve(TODAY, MealSlot.Dinner);

        assertThat(resolution.mealDate()).isEqualTo(TODAY);
        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Dinner);
        assertThat(resolution.adjusted()).isFalse();
    }

    @Test
    @DisplayName("14:59:59 is inside the dinner window")
    void dinnerJustBeforeCutoff() {
        assertThat(resolverAt("2026-03-24T14:59:59").resolve(TODAY, MealSlot.Dinner).adjusted()).isFalse();
    }

    @Test
    @DisplayName("15:00:00 sharp closes dinner and moves to next day's lunch")
    void dinnerCutoffIsInclusive() {
        MealSlotResolution resolution = resolverAt("2026-03-24T15:00:00").resolve(TODAY, MealSlot.Dinner);

        assertThat(resolution.mealDate()).isEqualTo(TOMORROW);
        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Lunch);
        assertThat(resolution.adjusted()).isTrue();
        assertThat(resolution.reasonCode()).isEqualTo(MealSlotResolution.Reason.DINNER_CUTOFF_PASSED);
    }

    @Test
    @DisplayName("lunch requested at 15:30 also rolls to next day's lunch")
    void lunchAfterDinnerCutoff() {
        MealSlotResolution resolution = resolverAt("2026-03-24T15:30:00").resolve(TODAY, MealSlot.Lunch);

        assertThat(resolution.mealDate()).isEqualTo(TOMORROW);
        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Lunch);
        assertThat(resolution.reasonCode()).isEqualTo(MealSlotResolution.Reason.DINNER_CUTOFF_PASSED);
    }

    @Test
    @DisplayName("a future date is never switched, its cut-off is always ahead")
    void futureDateNeverSwitches() {
        assertThat(resolverAt("2026-03-24T23:00:00").resolve(TOMORROW, MealSlot.Lunch).adjusted()).isFalse();
        assertThat(resolverAt("2026-03-24T23:00:00").resolve(TOMORROW, MealSlot.Dinner).adjusted()).isFalse();
    }

    @Test
    @DisplayName("the resolved meal is never empty and never recurses past next day")
    void alwaysResolvesToABookableMeal() {
        MealSlotResolution resolution = resolverAt("2026-03-24T23:59:59").resolve(TODAY, MealSlot.Dinner);

        assertThat(resolution.mealSlot()).isEqualTo(MealSlot.Lunch);
        assertThat(resolution.mealDate()).isEqualTo(TOMORROW);
        assertThat(resolution.adjusted()).isTrue();
    }

    @Test
    @DisplayName("a date in the past is rejected")
    void pastDateRejected() {
        MealSlotResolver resolver = resolverAt("2026-03-24T09:00:00");

        assertThatThrownBy(() -> resolver.resolve(TODAY.minusDays(1), MealSlot.Lunch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("past");
    }

    @Test
    @DisplayName("a date beyond the booking horizon is rejected")
    void farFutureDateRejected() {
        MealSlotResolver resolver = resolverAt("2026-03-24T09:00:00");

        assertThatThrownBy(() -> resolver.resolve(TODAY.plusDays(8), MealSlot.Lunch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at most 7 days");
    }

    @Test
    @DisplayName("nearestBookable walks lunch -> dinner -> next day lunch")
    void nearestBookable() {
        assertThat(resolverAt("2026-03-24T09:00:00").nearestBookable().mealSlot()).isEqualTo(MealSlot.Lunch);
        assertThat(resolverAt("2026-03-24T12:00:00").nearestBookable().mealSlot()).isEqualTo(MealSlot.Dinner);
        MealSlotResolution late = resolverAt("2026-03-24T16:00:00").nearestBookable();
        assertThat(late.mealSlot()).isEqualTo(MealSlot.Lunch);
        assertThat(late.mealDate()).isEqualTo(TOMORROW);
    }

    @Test
    @DisplayName("isBookable mirrors the cut-off decision")
    void isBookable() {
        assertThat(resolverAt("2026-03-24T09:00:00").isBookable(TODAY, MealSlot.Lunch)).isTrue();
        assertThat(resolverAt("2026-03-24T11:00:00").isBookable(TODAY, MealSlot.Lunch)).isFalse();
        assertThat(resolverAt("2026-03-24T11:00:00").isBookable(TODAY, MealSlot.Dinner)).isTrue();
    }
}
