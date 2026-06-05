package com.citydisruptors.auth_service.api.controller.rest;

import com.citydisruptors.auth_service.api.dto.AuthResponse;
import com.citydisruptors.auth_service.api.dto.UpdateUserRoleRequest;
import com.citydisruptors.auth_service.api.dto.UserResponse;
import com.citydisruptors.auth_service.service.OAuthService;
import com.citydisruptors.auth_service.service.UserManagementService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.citydisruptors.auth_service.entity.mapper.AuthMapper.toResponse;

@RestController
@RequestMapping("/auth")
public class AuthRESTController {

    private final OAuthService oAuthService;
    private final UserManagementService userManagementService;

    private final Counter testCounter;
    private final Timer testTimer;

    public AuthRESTController(OAuthService oAuthService, UserManagementService userManagementService, MeterRegistry registry) {
        this.oAuthService = oAuthService;
        this.userManagementService = userManagementService;

        this.testCounter = Counter.builder("auth.test.calls")
                .description("Calls to /auth/test")
                .register(registry);

        this.testTimer = registry.timer("auth.test.duration");
    }

    @GetMapping("/success")
    public ResponseEntity<AuthResponse> success(@AuthenticationPrincipal OAuth2User principal) {

        var result = oAuthService.processGoogleUser(principal);
        return ResponseEntity.ok(toResponse(result));
    }

    @GetMapping("/test")
    public String test() {
        return testTimer.record(() -> {
            testCounter.increment();
            return "AUTH OK";
        });
    }

    @GetMapping("/users")
    public List<UserResponse> getUsers() {
        return userManagementService.getUsers()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @PatchMapping("/users/role")
    public UserResponse updateUserRole(
            @RequestBody @Valid UpdateUserRoleRequest request
    ) {
        return UserResponse.from(
                userManagementService.updateRole(request.email(), request.role())
        );
    }
}
