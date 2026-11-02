package com.citydisruptors.auth_service.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditLoggerTest {

    private final SecurityAuditLogger securityAudit = new SecurityAuditLogger();
    private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityAuditLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private static User user() {
        User user = new User();
        user.setId(7L);
        user.setEmail("someone@example.com");
        user.setRole(Role.ROLE_ADMIN);
        return user;
    }

    private String lastMessage() {
        return appender.list.get(appender.list.size() - 1).getFormattedMessage();
    }

    @Test
    void logsSuccessfulLoginWithoutTheEmailAddress() {
        securityAudit.loginSucceeded(user());

        assertThat(lastMessage())
                .contains("securityEvent=LOGIN_SUCCESS", "userId=7", "role=ROLE_ADMIN")
                .doesNotContain("someone@example.com");
    }

    @Test
    void logsFailedLogin() {
        securityAudit.loginFailed("oauth2_access_denied");

        assertThat(lastMessage()).contains("securityEvent=LOGIN_FAILURE", "reason=oauth2_access_denied");
    }

    @Test
    void logsRoleChangeWithActor() {
        securityAudit.roleChanged(user(), Role.ROLE_USER, "1");

        assertThat(lastMessage()).contains("securityEvent=ROLE_CHANGED", "userId=7",
                "previousRole=ROLE_USER", "newRole=ROLE_ADMIN", "changedBy=1");
    }

    @Test
    void logsRoleChangeWithUnknownActor() {
        securityAudit.roleChanged(user(), Role.ROLE_USER, null);

        assertThat(lastMessage()).contains("changedBy=unknown");
    }
}
