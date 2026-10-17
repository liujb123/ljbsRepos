package com.webox.auth.dto;

import com.webox.user.User;

/** Public view of a user. Never exposes the password hash. */
public record UserResponse(Long id, String email, String displayName, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }
}
