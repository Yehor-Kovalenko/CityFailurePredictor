package com.citydisruptors.service_registry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.Bean;

import java.lang.management.ManagementFactory;


@EnableEurekaServer
@SpringBootApplication
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }

    @Bean
    public Gauge uptimeGauge(MeterRegistry registry) {
        return Gauge.builder("service.uptime",
                        () -> ManagementFactory.getRuntimeMXBean().getUptime())
                .register(registry);
    }

}
