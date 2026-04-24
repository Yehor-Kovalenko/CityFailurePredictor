package com.citydisruptors.auth_service.service;

import com.citydisruptors.auth_service.config.JwtService;
import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import com.citydisruptors.auth_service.entity.dto.AuthResult;
import com.citydisruptors.auth_service.repository.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class OAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public OAuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public AuthResult processGoogleUser(OAuth2User oAuth2User) {

        String email = oAuth2User.getAttribute("email");
        if (email == null) {
            throw new IllegalStateException("Email not provided by OAuth provider");
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

        String token = jwtService.generateToken(user);

        return new AuthResult(user, token);
    }
}
