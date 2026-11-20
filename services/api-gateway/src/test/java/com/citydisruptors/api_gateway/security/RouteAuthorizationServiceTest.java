package com.citydisruptors.api_gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAuthorizationServiceTest {

    private final RouteAuthorizationService service = new RouteAuthorizationService();

    @Test
    void userCanPlanRoutes() {
        assertThat(service.hasAccess("/ai/routes/optimize", HttpMethod.POST, "ROLE_USER")).isTrue();
    }

    @Test
    void userCannotReachOtherAiEndpoints() {
        assertThat(service.hasAccess("/ai/routes/optimize", HttpMethod.GET, "ROLE_USER")).isFalse();
        assertThat(service.hasAccess("/ai/health", HttpMethod.GET, "ROLE_USER")).isFalse();
        assertThat(service.hasAccess("/ai/routes/optimize/extra", HttpMethod.POST, "ROLE_USER")).isFalse();
    }

    @Test
    void adminCanReachAiService() {
        assertThat(service.hasAccess("/ai/health", HttpMethod.GET, "ROLE_ADMIN")).isTrue();
    }

    @Test
    void aiRoutesAreNotPublic() {
        assertThat(service.isPublicPath("/ai/routes/optimize")).isFalse();
        assertThat(service.hasAccess("/ai/routes/optimize", HttpMethod.POST, null)).isFalse();
    }
}
