package com.citydisruptors.service1.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> hello() {
        return Map.of("service", "service1", "status", "ok");
    }
}