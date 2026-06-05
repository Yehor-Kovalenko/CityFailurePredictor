package com.citydisruptors.auth_service.api.dto;

import com.citydisruptors.auth_service.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @Email @NotBlank String email,
        @NotNull Role role
) {
}