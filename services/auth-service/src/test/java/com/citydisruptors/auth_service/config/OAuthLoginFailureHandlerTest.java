package com.citydisruptors.auth_service.config;

import com.citydisruptors.auth_service.service.SecurityAuditLogger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OAuthLoginFailureHandlerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SecurityAuditLogger securityAudit = mock(SecurityAuditLogger.class);
    private final OAuthLoginFailureHandler handler = new OAuthLoginFailureHandler(registry, securityAudit);

    @Test
    void recordsOAuth2ErrorAndRedirectsToTheLoginPage() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(registry.counter("auth.login.failure", "reason", "oauth2_error").count()).isEqualTo(1);
        verify(securityAudit).loginFailed("oauth2_access_denied");
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
    }

    @Test
    void describesOtherAuthenticationFailuresByType() throws Exception {
        handler.onAuthenticationFailure(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new BadCredentialsException("bad"));

        verify(securityAudit).loginFailed("BadCredentialsException");
    }
}
