package com.citydisruptors.api_gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-32b";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new JwtService(SECRET), new RouteAuthorizationService(), new SecurityEventLogger(registry));
    private final AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
    private final WebFilterChain chain = exchange -> {
        forwarded.set(exchange.getRequest());
        return Mono.empty();
    };

    private static String token(String role) {
        return Jwts.builder()
                .subject("42")
                .claim("email", "user@example.com")
                .claim("role", role)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private MockServerWebExchange call(String path, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(path);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private double events(SecurityEventType type) {
        return registry.counter(SecurityEventLogger.METRIC, "type", type.name()).count();
    }

    @Test
    void rejectsRequestWithoutTokenAndRecordsAuthenticationFailure() {
        MockServerWebExchange exchange = call("/incidents/all", null);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
        assertThat(events(SecurityEventType.AUTHENTICATION_FAILED)).isEqualTo(1);
    }

    @Test
    void rejectsForgedTokenAndRecordsAuthenticationFailure() {
        MockServerWebExchange exchange = call("/incidents/all", "Bearer not-a-valid-token");

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(events(SecurityEventType.AUTHENTICATION_FAILED)).isEqualTo(1);
    }

    @Test
    void rejectsRoleWithoutAccessAndRecordsAccessDenied() {
        MockServerWebExchange exchange = call("/auth/users", "Bearer " + token("ROLE_USER"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forwarded.get()).isNull();
        assertThat(events(SecurityEventType.ACCESS_DENIED)).isEqualTo(1);
    }

    @Test
    void forwardsAuthorizedRequestWithUserHeaders() {
        call("/incidents/all", "Bearer " + token("ROLE_USER"));

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(headers.getFirst("X-User-Email")).isEqualTo("user@example.com");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("ROLE_USER");
        assertThat(registry.find(SecurityEventLogger.METRIC).counter()).isNull();
    }

    @Test
    void letsPublicPathsThroughWithoutToken() {
        call("/oauth2/authorization/google", null);

        assertThat(forwarded.get()).isNotNull();
        assertThat(registry.find(SecurityEventLogger.METRIC).counter()).isNull();
    }
}
