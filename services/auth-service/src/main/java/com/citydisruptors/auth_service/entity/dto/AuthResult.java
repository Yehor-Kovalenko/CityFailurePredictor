package com.citydisruptors.auth_service.entity.dto;

import com.citydisruptors.auth_service.entity.User;

public record AuthResult(
        User user,
        String token
) {
}
