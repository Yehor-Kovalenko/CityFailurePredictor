package com.citydisruptors.auth_service.api.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(
            String email,
            String role
    ) {
    }
}
