package com.citydisruptors.auth_service.config;

import com.citydisruptors.auth_service.api.dto.AuthResponse;
import com.citydisruptors.auth_service.entity.dto.AuthResult;
import com.citydisruptors.auth_service.service.OAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static com.citydisruptors.auth_service.entity.mapper.AuthMapper.toResponse;

@Configuration
public class SecurityConfig {

    private final OAuthService oAuthService;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    @Value("${app.swagger.allowed:false}")
    private boolean isSwaggerAllowed = false;

    public SecurityConfig(OAuthService oAuthService,
                          ObjectMapper objectMapper,
                          JwtService jwtService) {
        this.oAuthService = oAuthService;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService);

        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                )

                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/**").permitAll();
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();

                    if (isSwaggerAllowed) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }

                    auth.anyRequest().authenticated();
                })

                .oauth2Login(oauth -> oauth
                        .successHandler((request, response, authentication) -> {

                            OAuth2User principal = (OAuth2User) authentication.getPrincipal();

                            AuthResult result = oAuthService.processGoogleUser(principal);

                            AuthResponse responseBody = toResponse(result);

                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                            objectMapper.writeValue(response.getWriter(), responseBody);
                        })
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
