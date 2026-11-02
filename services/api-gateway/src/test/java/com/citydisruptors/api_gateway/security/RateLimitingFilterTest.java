package com.citydisruptors.api_gateway.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SecurityEventLogger securityEvents = new SecurityEventLogger(registry);
    private final AtomicInteger passed = new AtomicInteger();
    private final WebFilterChain chain = exchange -> {
        passed.incrementAndGet();
        return Mono.empty();
    };

    private static MockServerWebExchange request(String path, String ip) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress(ip, 40000)));
    }

    private RateLimitingFilter filter(boolean enabled, int capacity) {
        return new RateLimitingFilter(enabled, capacity, 0.001, 100, securityEvents);
    }

    @Test
    void passesRequestsWithinTheLimit() {
        RateLimitingFilter filter = filter(true, 2);

        filter.filter(request("/incidents/all", "10.0.0.1"), chain).block();
        filter.filter(request("/incidents/all", "10.0.0.1"), chain).block();

        assertThat(passed).hasValue(2);
        assertThat(registry.find(SecurityEventLogger.METRIC).counter()).isNull();
    }

    @Test
    void rejectsRequestsOverTheLimitWith429AndRecordsTheEvent() {
        RateLimitingFilter filter = filter(true, 1);
        filter.filter(request("/incidents/all", "10.0.0.1"), chain).block();

        MockServerWebExchange rejected = request("/incidents/all", "10.0.0.1");
        filter.filter(rejected, chain).block();

        assertThat(passed).hasValue(1);
        assertThat(rejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rejected.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(registry.counter(SecurityEventLogger.METRIC, "type", "RATE_LIMITED").count()).isEqualTo(1);
    }

    @Test
    void neverThrottlesActuatorEndpoints() {
        RateLimitingFilter filter = filter(true, 1);

        for (int i = 0; i < 5; i++) {
            filter.filter(request("/actuator/health", "10.0.0.1"), chain).block();
        }

        assertThat(passed).hasValue(5);
    }

    @Test
    void passesEverythingWhenDisabled() {
        RateLimitingFilter filter = filter(false, 1);

        for (int i = 0; i < 5; i++) {
            filter.filter(request("/incidents/all", "10.0.0.1"), chain).block();
        }

        assertThat(passed).hasValue(5);
    }
}
