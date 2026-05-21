package com.citydisruptors.api_gateway;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
    public Mono<Map<String, Object>> getProxyDocs(@PathVariable String serviceName) {
        String downstreamUrl = "http://localhost:" + this.gatewayPort + "/" + serviceName + "/v3/api-docs";

        return webClient.get()
                .uri(downstreamUrl)
                .retrieve()
                .bodyToMono(Map.class)
                .map(originalJson -> {
                    originalJson.put("servers", List.of(
                            Map.of(
                                    "url", "/" + serviceName,
                                    "description", "Gateway Route Proxy"
                            )
                    ));
                    return originalJson;
                });
    }
}
