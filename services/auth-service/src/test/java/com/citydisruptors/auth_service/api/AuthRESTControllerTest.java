package com.citydisruptors.auth_service.api;

import com.citydisruptors.auth_service.api.controller.rest.AuthRESTController;
import com.citydisruptors.auth_service.api.dto.UpdateUserRoleRequest;
import com.citydisruptors.auth_service.api.dto.UserResponse;
import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import com.citydisruptors.auth_service.service.OAuthService;
import com.citydisruptors.auth_service.service.UserManagementService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthRESTControllerTest {

    private final UserManagementService userManagementService = mock(UserManagementService.class);
    private final AuthRESTController controller = new AuthRESTController(
            mock(OAuthService.class), userManagementService, new SimpleMeterRegistry());

    @Test
    void passesTheCallingAdminToTheRoleChange() {
        User user = new User();
        user.setId(5L);
        user.setEmail("user@example.com");
        user.setRole(Role.ROLE_ADMIN);
        when(userManagementService.updateRole("user@example.com", Role.ROLE_ADMIN, "1")).thenReturn(user);

        UserResponse response = controller.updateUserRole(
                new UpdateUserRoleRequest("user@example.com", Role.ROLE_ADMIN), "1");

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.role()).isEqualTo(Role.ROLE_ADMIN);
    }
}
