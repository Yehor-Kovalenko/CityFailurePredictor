package com.citydisruptors.api_gateway.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Audit trail of rejected requests.
 * Every event is written as a structured log line (the JSON fields reach Loki, where
 * "securityEvent" is a label) and counted in the gateway_security_events_total metric.
 */
@Component
public class SecurityEventLogger {

    static final String METRIC = "gateway.security.events";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_MDC_KEY = "correlationId";
    private static final Logger log = LoggerFactory.getLogger(SecurityEventLogger.class);

    private final MeterRegistry registry;

    public SecurityEventLogger(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(SecurityEventType type, ServerWebExchange exchange, String reason) {
        ServerHttpRequest request = exchange.getRequest();
        registry.counter(METRIC, "type", type.name()).increment();

        String correlationId = request.getHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId != null) {
            MDC.put(CORRELATION_MDC_KEY, correlationId);
        }
        try {
            log.warn("Security event {} {} {} {} {}",
                    kv("securityEvent", type.name()),
                    kv("reason", reason),
                    kv("method", request.getMethod().name()),
                    kv("path", request.getPath().value()),
                    kv("clientIp", clientIp(request)));
        } finally {
            MDC.remove(CORRELATION_MDC_KEY);
        }
    }

    static String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
    }
}
