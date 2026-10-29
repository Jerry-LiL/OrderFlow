package com.orderflow.dto.response;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String username,
        String email,
        String role
) {
    public static AuthResponse of(String token, Long userId, String username, String email, String role) {
        return new AuthResponse(token, "Bearer", userId, username, email, role);
    }
}
