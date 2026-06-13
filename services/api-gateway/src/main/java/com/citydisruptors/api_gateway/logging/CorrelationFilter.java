package com.citydisruptors.api_gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Optional;
import java.util.UUID;

@Component
public class CorrelationFilter implements WebFilter {

    private static final String HEADER = "X-Correlation-ID";
    static final String MDC_KEY = "correlationId";
    private static final Logger log = LoggerFactory.getLogger(CorrelationFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String correlationId = Optional
                .ofNullable(exchange.getRequest().getHeaders().getFirst(HEADER))
                .orElse(UUID.randomUUID().toString());

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, correlationId))
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Set on response so the original caller gets it back
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        return chain.filter(mutatedExchange)
                .contextWrite(Context.of(MDC_KEY, correlationId))
                .doFirst(() -> {
                    MDC.put(MDC_KEY, correlationId);
                    log.info("Api gateway hit with target route: {}", path);
                    MDC.remove(MDC_KEY);
                });
    }
}
