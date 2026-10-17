package com.webox.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Search keywords are bound as parameters (never concatenated), and their wildcard characters
 * are neutralized so a user cannot turn a search into a full-table scan.
 */
class SearchKeywordTest {

    @Test
    @DisplayName("normalization trims, lower-cases and truncates to 50 characters")
    void normalize() {
        assertThat(SearchKeyword.normalize("  Chicken  ")).isEqualTo("chicken");
        assertThat(SearchKeyword.normalize("CHICKEN")).isEqualTo("chicken");
        assertThat(SearchKeyword.normalize("")).isNull();
        assertThat(SearchKeyword.normalize("   ")).isNull();
        assertThat(SearchKeyword.normalize(null)).isNull();
        assertThat(SearchKeyword.normalize("x".repeat(80))).hasSize(50);
    }

    @Test
    @DisplayName("a plain keyword becomes a contains-pattern")
    void plainPattern() {
        assertThat(SearchKeyword.toLikePattern("chicken")).isEqualTo("%chicken%");
    }

    @Test
    @DisplayName("percent is escaped so it cannot match everything")
    void percentEscaped() {
        assertThat(SearchKeyword.toLikePattern("%")).isEqualTo("%\\%%");
        assertThat(SearchKeyword.toLikePattern("100%")).isEqualTo("%100\\%%");
    }

    @Test
    @DisplayName("underscore is escaped so it cannot match any single character")
    void underscoreEscaped() {
        assertThat(SearchKeyword.toLikePattern("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    @DisplayName("the escape character itself is escaped first")
    void backslashEscaped() {
        assertThat(SearchKeyword.toLikePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    @DisplayName("an injection-style keyword survives as an inert literal")
    void injectionStyleKeywordIsInert() {
        String pattern = SearchKeyword.toLikePattern(SearchKeyword.normalize("' OR 1=1 --"));

        assertThat(pattern).isEqualTo("%' or 1=1 --%");
        assertThat(pattern).doesNotContain("1=1%'");
    }

    @Test
    @DisplayName("null and blank keywords produce no pattern")
    void blankProducesNoPattern() {
        assertThat(SearchKeyword.toLikePattern(null)).isNull();
        assertThat(SearchKeyword.toLikePatternFromRaw("  ")).isNull();
    }

    @Test
    @DisplayName("in-memory matching spans name and description, case-insensitively")
    void matches() {
        assertThat(SearchKeyword.matches("chicken", "Kung Pao Chicken", "spicy")) .isTrue();
        assertThat(SearchKeyword.matches("chicken", "Mapo Tofu", "chicken with tofu")).isTrue();
        assertThat(SearchKeyword.matches("chicken", "Mapo Tofu", "spicy tofu")).isFalse();
        assertThat(SearchKeyword.matches(null, "anything", null)).isTrue();
    }
}
