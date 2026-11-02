package com.citydisruptors.api_gateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Per-client rate limiting on the gateway.
 * Runs before token validation, so floods of requests with missing or forged tokens are
 * throttled too. Clients are identified by their IP address; rejected requests get
 * 429 Too Many Requests and are recorded as a security event.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter implements WebFilter {

    private final boolean enabled;
    private final TokenBucketRateLimiter rateLimiter;
    private final SecurityEventLogger securityEvents;

    @Autowired
    public RateLimitingFilter(
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.rate-limit.capacity:60}") int capacity,
            @Value("${app.security.rate-limit.refill-per-second:20}") double refillPerSecond,
            @Value("${app.security.rate-limit.max-tracked-clients:10000}") int maxTrackedClients,
            SecurityEventLogger securityEvents
    ) {
        this(enabled, new TokenBucketRateLimiter(capacity, refillPerSecond, maxTrackedClients, System::nanoTime),
                securityEvents);
    }

    RateLimitingFilter(boolean enabled, TokenBucketRateLimiter rateLimiter, SecurityEventLogger securityEvents) {
        this.enabled = enabled;
        this.rateLimiter = rateLimiter;
        this.securityEvents = securityEvents;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled || exchange.getRequest().getPath().value().startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        if (rateLimiter.tryAcquire(SecurityEventLogger.clientIp(exchange.getRequest()))) {
            return chain.filter(exchange);
        }

        securityEvents.record(SecurityEventType.RATE_LIMITED, exchange, "Request rate limit exceeded");
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        return exchange.getResponse().setComplete();
    }
}
