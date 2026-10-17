package com.webox.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload.
 *
 * <p>Only presence and length are validated here. Format checks are deliberately absent so
 * a malformed address cannot be distinguished from a wrong password in the response.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required.")
        @Size(max = 200, message = "Email must be 200 characters or fewer.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 64, message = "Password must be 64 characters or fewer.")
        String password) {
}
