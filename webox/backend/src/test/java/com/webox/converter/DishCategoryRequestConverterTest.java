package com.webox.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.webox.dish.DishCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Query-parameter binding for {@link DishCategory} (PRD 3.2 and 4.3).
 *
 * <p>Regression cover for the defect where the API contract used the English display label
 * ("Light Meal") while Spring's default enum converter expected the Java constant name
 * ("LIGHT_MEAL"), so every {@code ?categories=...} request was rejected with 400 - and the cuisine
 * filter was dead on both the employee menu and the Console dish table.
 */
class DishCategoryRequestConverterTest {

    private final DishCategoryRequestConverter converter = new DishCategoryRequestConverter();

    @ParameterizedTest
    @CsvSource({
            "Chinese, CHINESE",
            "Western, WESTERN",
            "Japanese, JAPANESE",
            "Korean, KOREAN",
            "Light Meal, LIGHT_MEAL",
            "Southeast Asian, SOUTHEAST_ASIAN"
    })
    @DisplayName("every English display label binds, including the one containing a space")
    void bindsDisplayLabel(String label, DishCategory expected) {
        assertThat(converter.convert(label)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHINESE", "WESTERN", "JAPANESE", "KOREAN", "LIGHT_MEAL", "SOUTHEAST_ASIAN"})
    @DisplayName("the Java constant name keeps working, so no existing caller breaks")
    void bindsConstantName(String constantName) {
        assertThat(converter.convert(constantName)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"chinese", "CHINESE", "Chinese", "  Chinese  ", "lIgHt MeAl", "light_meal"})
    @DisplayName("binding is case-insensitive and tolerates surrounding whitespace")
    void isLenientAboutCaseAndWhitespace(String raw) {
        assertThat(converter.convert(raw)).isEqualTo(DishCategory.fromLabel(raw.trim()));
    }

    @Test
    @DisplayName("a space that arrived as a URL '+' (decoded to 'Light Meal') binds")
    void bindsUrlDecodedSpacedLabel() {
        // The SPA builds the query with URLSearchParams, which encodes the space in "Light Meal"
        // as '+'. Spring decodes it back to a space before this converter sees it.
        assertThat(converter.convert("Light Meal")).isEqualTo(DishCategory.LIGHT_MEAL);
    }

    @Test
    @DisplayName("every enum value round-trips through its own label")
    void everyValueRoundTrips() {
        for (DishCategory value : DishCategory.values()) {
            assertThat(converter.convert(value.label())).isEqualTo(value);
        }
    }

    @Test
    @DisplayName("a null source yields null rather than throwing")
    void nullSourceIsNull() {
        assertThat(converter.convert(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Seafood", "Unknown", "Chinese,Japanese", "123", "", "   "})
    @DisplayName("an unknown or blank value throws, so the request is a 400 and never a 500")
    void unknownValueThrows(String raw) {
        // Throwing (instead of returning null) matters: a null element would reach
        // MenuService#toCategorySet and surface as an EnumSet.copyOf NullPointerException -> 500.
        assertThatThrownBy(() -> converter.convert(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown dish category");
    }
}
