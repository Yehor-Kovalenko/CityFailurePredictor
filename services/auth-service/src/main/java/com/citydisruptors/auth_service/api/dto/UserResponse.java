package com.citydisruptors.auth_service.api.dto;

import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;

public record UserResponse(
        Long id,
        String email,
        String username,
        Role role,
        String provider
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.getProvider()
        );
    }
}