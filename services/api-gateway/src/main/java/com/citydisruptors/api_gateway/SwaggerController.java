package com.citydisruptors.api_gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SwaggerController {

    @Autowired
    private ReactiveDiscoveryClient discoveryClient;

    private final WebClient webClient = WebClient.create();

    @Value("${server.port}")
    private String gatewayPort;

    @GetMapping("/swagger-config")
    public Mono<Map<String, Object>> swaggerUrls() {
        return discoveryClient.getServices()
                .filter(serviceId -> !serviceId.contains("gateway"))
                .filter(serviceId -> !serviceId.contains("config"))
                .map(serviceId -> Map.of(
                        "name", serviceId,
                        "url", "/v3/api-docs-proxy/" + serviceId
                ))
                .collectList()
                .map(urls -> Map.of("urls", urls));
    }

    /**
     * Proxy for swagger-docs from each of the services. Prepends the service name to each endpoint of the doc, so that the centralized swagger will work correctly and send correct requests
     * @param serviceName
     * @return
     */
    @GetMapping("/v3/api-docs-proxy/{serviceName}")
    public Mono<Map<String, Object>> getProxyDocs(@PathVariable String serviceName, ServerHttpRequest request) {
        String downstreamUrl = "http://localhost:" + this.gatewayPort + "/" + serviceName + "/v3/api-docs";
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        return webClient.get()
                .uri(downstreamUrl)
                .headers(headers -> {
                    if (authHeader != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
                    }
                })
                .retrieve()
                .bodyToMono(Map.class)
                .map(originalJson -> {
                    originalJson.put("servers", List.of(
                            Map.of(
                                    "url", "/" + serviceName,
                                    "description", "Gateway Route Proxy"
                            )
                    ));

                    // injecting auth part
                    List<Map<String, List<String>>> security = new ArrayList<>();
                    security.add(Map.of("bearerAuth", List.of()));
                    originalJson.put("security", security);

                    // 3. Inject Security Schemes into Components
                    Map<String, Object> components = (Map<String, Object>) originalJson.getOrDefault("components", new HashMap<>());
                    Map<String, Object> securitySchemes = (Map<String, Object>) components.getOrDefault("securitySchemes", new HashMap<>());

                    securitySchemes.put("bearerAuth", Map.of(
                            "type", "http",
                            "scheme", "bearer",
                            "bearerFormat", "JWT"
                    ));

                    components.put("securitySchemes", securitySchemes);
                    originalJson.put("components", components);

                    return originalJson;
                });
    }
}
