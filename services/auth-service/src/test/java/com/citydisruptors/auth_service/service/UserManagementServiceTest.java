package com.citydisruptors.auth_service.service;

import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import com.citydisruptors.auth_service.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserManagementServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecurityAuditLogger securityAudit = mock(SecurityAuditLogger.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UserManagementService service =
            new UserManagementService(userRepository, registry, securityAudit);

    @Test
    void updatesRoleAndAuditsTheChange() {
        User user = new User();
        user.setId(3L);
        user.setEmail("user@example.com");
        user.setRole(Role.ROLE_USER);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updated = service.updateRole("user@example.com", Role.ROLE_ADMIN, "1");

        assertThat(updated.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(registry.counter("auth.users.role.updated", "role", "ROLE_ADMIN").count()).isEqualTo(1);
        verify(securityAudit).roleChanged(updated, Role.ROLE_USER, "1");
    }

    @Test
    void rejectsUnknownUserWithoutAuditing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole("missing@example.com", Role.ROLE_ADMIN, "1"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(securityAudit);
    }
}
