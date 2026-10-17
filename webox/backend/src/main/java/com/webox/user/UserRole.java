package com.webox.user;

/** Application role. Spring Security authority is {@code ROLE_<name>}. */
public enum UserRole {
    EMPLOYEE,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
