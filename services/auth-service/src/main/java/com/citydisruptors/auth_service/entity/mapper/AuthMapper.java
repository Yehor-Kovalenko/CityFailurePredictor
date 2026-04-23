package com.citydisruptors.auth_service.entity.mapper;

import com.citydisruptors.auth_service.api.dto.AuthResponse;
import com.citydisruptors.auth_service.entity.dto.AuthResult;

public class AuthMapper {

    public static AuthResponse toResponse(AuthResult result) {
        return new AuthResponse(
                result.token(),
                "Bearer",
                86400,
                new AuthResponse.UserInfo(
                        result.user().getEmail(),
                        result.user().getRole().name()
                )
        );
    }
}
