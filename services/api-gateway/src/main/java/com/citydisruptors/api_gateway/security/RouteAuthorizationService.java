package com.citydisruptors.api_gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteAuthorizationService {

    private static final List<String> PUBLIC_PATHS = List.of(
            // Auth public
            "/auth/test",
            "/auth/success",
            "/oauth2",
            "/login",

            // Central Swagger
            "/swagger-ui",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs-proxy",
            "/swagger-config",
            "/webjars",

            // Service Swagger through prefixed routes
            "/auth-service/v3/api-docs",
            "/incident-service/v3/api-docs",
            "/data-ingestion-service/v3/api-docs",
            "/decision-service/v3/api-docs",

            // Actuator gateway
            "/actuator"
    );

    public boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::contains);
    }

    public boolean hasAccess(String path, HttpMethod method, String role) {
        if (role == null || role.isBlank()) {
            return false;
        }

        if (isAdmin(role)) {
            return true;
        }

        if (isUser(role)) {
            return hasUserAccess(path, method);
        }

        return false;
    }

    private boolean hasUserAccess(String path, HttpMethod method) {
        /*
         * Auth Service
         */
        if (path.startsWith("/auth/users")) {
            return false;
        }

        /*
         * Data Ingestion Service
         */
        if (path.startsWith("/data-ingestion/test")) {
            return method == HttpMethod.GET;
        }

        if (path.startsWith("/data-ingestion/datasets")) {
            return method == HttpMethod.GET;
        }

        if (path.startsWith("/data-ingestion/electricity/import")) {
            return false;
        }

        if (path.startsWith("/data-ingestion/electricity")) {
            return false;
        }

        /*
         * Incident Service
         */
        if (path.startsWith("/incident-service/") || path.startsWith("/incidents/")) {
            return true;
        }
        // TODO add guard so that only user that created incident can check its status and update it


        return false;
    }

    private boolean isAdmin(String role) {
        return "ROLE_ADMIN".equals(role);
    }

    private boolean isUser(String role) {
        return "ROLE_USER".equals(role);
    }
}