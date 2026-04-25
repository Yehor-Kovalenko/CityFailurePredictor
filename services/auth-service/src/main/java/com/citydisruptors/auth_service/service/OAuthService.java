package com.citydisruptors.auth_service.service;

import com.citydisruptors.auth_service.config.JwtService;
import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import com.citydisruptors.auth_service.entity.dto.AuthResult;
import com.citydisruptors.auth_service.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final MeterRegistry registry;

    private final Timer loginTimer;

    public OAuthService(UserRepository userRepository,
                        JwtService jwtService,
                        MeterRegistry registry) {

        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.registry = registry;

        this.loginTimer = Timer.builder("auth.login.duration")
                .description("Login duration")
                .publishPercentileHistogram()
                .register(registry);
    }

    public AuthResult processGoogleUser(OAuth2User oAuth2User) {

        return loginTimer.record(() -> {
            try {
                String email = oAuth2User.getAttribute("email");

                if (email == null) {
                    registry.counter("auth.login.failure", "reason", "missing_email").increment();
                    throw new IllegalStateException("Email not provided");
                }

                String name = oAuth2User.getAttribute("name");
                String sub = oAuth2User.getAttribute("sub");

                User user = userRepository.findByEmail(email)
                        .orElseGet(() -> {
                            User u = new User();
                            u.setEmail(email);
                            u.setUsername(name);
                            u.setRole(Role.ROLE_USER);
                            u.setProvider("GOOGLE");
                            u.setProviderId(sub);
                            return userRepository.save(u);
                        });

                registry.counter("auth.login.success").increment();

                String token = jwtService.generateToken(user);

                return new AuthResult(user, token);

            } catch (Exception e) {
                registry.counter("auth.login.failure", "reason", "exception").increment();
                throw e;
            }
        });
    }
}
