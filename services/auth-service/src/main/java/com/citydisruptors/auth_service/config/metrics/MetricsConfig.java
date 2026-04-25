package com.citydisruptors.auth_service.config.metrics;

import com.citydisruptors.auth_service.repository.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    public MetricsConfig(MeterRegistry registry, UserRepository userRepository) {

        Gauge.builder("auth.users.count", userRepository, UserRepository::count)
                .description("Total users in system")
                .tag("service", "auth-service")
                .register(registry);
    }
}
