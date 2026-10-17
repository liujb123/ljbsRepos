package com.webox.security;

import com.webox.user.UserRole;

/**
 * Authenticated caller, extracted from a verified JWT.
 *
 * <p>Note: the role is taken from the token claim, so a role change does not take effect
 * until the token expires. A production deployment would re-validate against the database
 * or keep a token version to allow immediate revocation.
 */
public record AuthPrincipal(Long userId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
