package com.webox.security;

/**
 * Password policy (PRD 3.1): at least 8 characters, containing both a letter and a digit,
 * with no whitespace. Exposed as a constant so the DTO annotation and the unit tests share
 * exactly one definition.
 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,64}$";

    public static final String MESSAGE =
            "Password must be at least 8 characters and include both letters and numbers.";

    private PasswordPolicy() {
    }

    public static boolean isValid(String rawPassword) {
        return rawPassword != null && rawPassword.matches(REGEX);
    }
}
