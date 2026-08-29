package com.bishi.cs.security;

import com.bishi.cs.user.UserAccount;

public record AuthUser(Long id, String email, String phone, String role) {
    public static AuthUser from(UserAccount user) {
        String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole();
        return new AuthUser(user.getId(), user.getEmail(), user.getPhone(), role);
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
