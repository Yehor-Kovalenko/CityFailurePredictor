package com.citydisruptors.auth_service.api;

import com.citydisruptors.auth_service.api.dto.AuthResponse;
import com.citydisruptors.auth_service.service.OAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.citydisruptors.auth_service.entity.mapper.AuthMapper.toResponse;

@RestController
@RequestMapping("/auth")
public class AuthRESTController {

    private final OAuthService oAuthService;

    public AuthRESTController(OAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    @GetMapping("/success")
    public ResponseEntity<AuthResponse> success(@AuthenticationPrincipal OAuth2User principal) {

        var result = oAuthService.processGoogleUser(principal);

        return ResponseEntity.ok(toResponse(result));
    }

    @GetMapping("/test")
    public String test() {
        return "AUTH OK";
    }
}
