package com.citydisruptors.auth_service.config;

import com.citydisruptors.auth_service.service.SecurityAuditLogger;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Records a failed Google sign-in (e.g. consent refused, invalid state or code) as a
 * security event and in the auth.login.failure metric, then keeps Spring Security's
 * default behaviour of redirecting to the login page with an error.
 */
@Component
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    private final MeterRegistry registry;
    private final SecurityAuditLogger securityAudit;
    private final AuthenticationFailureHandler delegate = new SimpleUrlAuthenticationFailureHandler("/login?error");

    public OAuthLoginFailureHandler(MeterRegistry registry, SecurityAuditLogger securityAudit) {
        this.registry = registry;
        this.securityAudit = securityAudit;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        registry.counter("auth.login.failure", "reason", "oauth2_error").increment();
        securityAudit.loginFailed(describe(exception));
        delegate.onAuthenticationFailure(request, response, exception);
    }

    private static String describe(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            return "oauth2_" + oauth2.getError().getErrorCode();
        }
        return exception.getClass().getSimpleName();
    }
}
