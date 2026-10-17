package com.webox.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** PRD 3.1: at least 8 characters, containing both letters and digits, no whitespace. */
class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234", "Passw0rd", "a1b2c3d4", "Admin1234", "Webox2026"})
    @DisplayName("passwords meeting the policy are accepted")
    void acceptsValidPasswords(String password) {
        assertThat(PasswordPolicy.isValid(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdefgh",      // letters only
            "12345678",      // digits only
            "abc123",        // too short
            "a1 b2c3d4",     // contains whitespace
            "abcdefg1 "      // trailing whitespace
    })
    @DisplayName("passwords violating the policy are rejected")
    void rejectsInvalidPasswords(String password) {
        assertThat(PasswordPolicy.isValid(password)).isFalse();
    }

    @Test
    @DisplayName("null is rejected")
    void rejectsNull() {
        assertThat(PasswordPolicy.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("the eight-character boundary is inclusive")
    void lengthBoundary() {
        assertThat(PasswordPolicy.isValid("abcd123")).isFalse();
        assertThat(PasswordPolicy.isValid("abcd1234")).isTrue();
    }

    @Test
    @DisplayName("a 64-character password is accepted and 65 is not")
    void maximumLength() {
        assertThat(PasswordPolicy.isValid("a1" + "x".repeat(62))).isTrue();
        assertThat(PasswordPolicy.isValid("a1" + "x".repeat(63))).isFalse();
    }
}
