package com.citydisruptors.api_gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class SwaggerController {

    @Autowired
    private ReactiveDiscoveryClient discoveryClient;

    @GetMapping("/swagger-config")
    public Mono<Map<String, Object>> swaggerUrls() {
        return discoveryClient.getServices()
                .filter(serviceId -> !serviceId.contains("gateway"))
                .filter(serviceId -> !serviceId.contains("config"))
                .map(serviceId -> Map.of(
                        "name", serviceId,
                        "url", "/" + serviceId + "/v3/api-docs"
                ))
                .collectList()
                .map(urls -> Map.of("urls", urls));
    }
}
