package com.citydisruptors.api_gateway.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventLoggerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SecurityEventLogger securityEvents = new SecurityEventLogger(registry);
    private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.class);
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

    @Test
    void countsAndLogsTheEventWithRequestDetails() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/users")
                        .remoteAddress(new InetSocketAddress("10.1.2.3", 5555))
                        .header("X-Correlation-ID", "corr-1"));

        securityEvents.record(SecurityEventType.ACCESS_DENIED, exchange, "Role ROLE_USER is not allowed");

        assertThat(registry.counter(SecurityEventLogger.METRIC, "type", "ACCESS_DENIED").count()).isEqualTo(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage())
                .contains("securityEvent=ACCESS_DENIED", "reason=Role ROLE_USER is not allowed",
                        "method=GET", "path=/auth/users", "clientIp=10.1.2.3");
        assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", "corr-1");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void logsWithoutCorrelationIdWhenTheHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/incidents/all"));

        securityEvents.record(SecurityEventType.AUTHENTICATION_FAILED, exchange, "Missing bearer token");

        assertThat(registry.counter(SecurityEventLogger.METRIC, "type", "AUTHENTICATION_FAILED").count()).isEqualTo(1);
        assertThat(appender.list.get(0).getMDCPropertyMap()).doesNotContainKey("correlationId");
    }

    @Test
    void resolvesTheClientAddress() {
        assertThat(SecurityEventLogger.clientIp(MockServerHttpRequest.get("/").build())).isEqualTo("unknown");
        assertThat(SecurityEventLogger.clientIp(MockServerHttpRequest.get("/")
                .remoteAddress(InetSocketAddress.createUnresolved("proxy.local", 80)).build()))
                .isEqualTo("proxy.local");
        assertThat(SecurityEventLogger.clientIp(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("192.168.0.7", 80)).build()))
                .isEqualTo("192.168.0.7");
    }
}
