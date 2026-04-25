package com.citydisruptors.api_gateway.config.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@Component
public class GatewayMetricsFilter implements GlobalFilter {

    private final MeterRegistry registry;

    public GatewayMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        long start = System.currentTimeMillis();

        return chain.filter(exchange).doFinally(signalType -> {

            long duration = System.currentTimeMillis() - start;

            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();
            String status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().toString()
                    : "UNKNOWN";

            registry.timer(
                    "gateway.requests.duration",
                    "path", path,
                    "method", method,
                    "status", status
            ).record(duration, TimeUnit.MILLISECONDS);

            if (exchange.getResponse().getStatusCode() != null &&
                    exchange.getResponse().getStatusCode().is5xxServerError()) {

                registry.counter("gateway.errors.total").increment();
            }
        });
    }
}
