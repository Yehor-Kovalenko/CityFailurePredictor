package com.citydisruptors.auth_service.service;

import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Audit trail of authentication events.
 * The structured fields reach Loki, where "securityEvent" is a label. Users are identified
 * by their id only; e-mail addresses are personal data and stay out of the logs.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);

    public void loginSucceeded(User user) {
        log.info("Security event {} {} {}",
                kv("securityEvent", "LOGIN_SUCCESS"),
                kv("userId", user.getId()),
                kv("role", user.getRole()));
    }

    public void loginFailed(String reason) {
        log.warn("Security event {} {}",
                kv("securityEvent", "LOGIN_FAILURE"),
                kv("reason", reason));
    }

    public void roleChanged(User user, Role previousRole, String changedBy) {
        log.info("Security event {} {} {} {} {}",
                kv("securityEvent", "ROLE_CHANGED"),
                kv("userId", user.getId()),
                kv("previousRole", previousRole),
                kv("newRole", user.getRole()),
                kv("changedBy", changedBy == null ? "unknown" : changedBy));
    }
}
