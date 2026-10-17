package com.webox.auth.dto;

import com.webox.security.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registration payload (PRD 3.1). Limits mirror the rules documented in BUSINESS-DESIGN 10.1. */
public record RegisterRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Please enter a valid email address.")
        @Size(max = 200, message = "Email must be 200 characters or fewer.")
        String email,

        @NotBlank(message = "Password is required.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @NotBlank(message = "Display name is required.")
        @Size(max = 50, message = "Display name must be 50 characters or fewer.")
        String displayName) {
}
