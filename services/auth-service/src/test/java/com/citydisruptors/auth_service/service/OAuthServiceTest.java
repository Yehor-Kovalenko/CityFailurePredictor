package com.citydisruptors.auth_service.service;

import com.citydisruptors.auth_service.config.JwtService;
import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import com.citydisruptors.auth_service.entity.dto.AuthResult;
import com.citydisruptors.auth_service.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final SecurityAuditLogger securityAudit = mock(SecurityAuditLogger.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OAuthService oAuthService =
            new OAuthService(userRepository, jwtService, registry, securityAudit, "admin@example.com");

    private static OAuth2User googleUser(String email) {
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttribute("email")).thenReturn(email);
        when(principal.getAttribute("name")).thenReturn("Jane");
        when(principal.getAttribute("sub")).thenReturn("google-sub");
        return principal;
    }

    @Test
    void createsUserIssuesTokenAndAuditsTheLogin() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt");

        AuthResult result = oAuthService.processGoogleUser(googleUser("admin@example.com"));

        assertThat(result.token()).isEqualTo("jwt");
        assertThat(result.user().getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(registry.counter("auth.login.success").count()).isEqualTo(1);
        verify(securityAudit).loginSucceeded(result.user());
    }

    @Test
    void auditsAndCountsALoginWithoutEmail() {
        OAuth2User principal = googleUser(null);

        assertThatThrownBy(() -> oAuthService.processGoogleUser(principal))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.counter("auth.login.failure", "reason", "missing_email").count()).isEqualTo(1);
        verify(securityAudit).loginFailed("Email not provided");
        verify(securityAudit, never()).loginSucceeded(any());
    }
}
