package com.webox.common;

import java.util.Locale;

/**
 * Normalization and escaping for user-supplied search keywords.
 *
 * <p>Two distinct problems are solved here:
 * <ol>
 *   <li><b>Injection surface</b> - the keyword is always bound as a query parameter, never
 *       concatenated. This class adds no SQL text of its own.</li>
 *   <li><b>Wildcard pollution</b> - a user typing {@code %} or {@code _} must not turn into a
 *       LIKE wildcard, so those characters (and the escape character itself) are escaped and
 *       the query declares {@code ESCAPE '\'}.</li>
 * </ol>
 */
public final class SearchKeyword {

    /** PRD 3.1: search keywords are limited to 50 characters. */
    public static final int MAX_LENGTH = 50;

    private SearchKeyword() {
    }

    /** Trims, lower-cases and truncates. Returns {@code null} for a blank keyword. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /** Builds a {@code %keyword%} LIKE pattern with {@code \}, {@code %} and {@code _} escaped. */
    public static String toLikePattern(String normalized) {
        if (normalized == null) {
            return null;
        }
        String escaped = normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Convenience for the common "normalize then build a pattern" case. */
    public static String toLikePatternFromRaw(String raw) {
        return toLikePattern(normalize(raw));
    }

    /** Case-insensitive substring test used by the in-memory menu filter. */
    public static boolean matches(String normalizedKeyword, String... haystacks) {
        if (normalizedKeyword == null) {
            return true;
        }
        for (String haystack : haystacks) {
            if (haystack != null && haystack.toLowerCase(Locale.ROOT).contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }
}
