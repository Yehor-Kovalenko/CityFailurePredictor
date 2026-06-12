package com.citydisruptors.reporting_service.client;

import com.citydisruptors.reporting_service.client.dto.DecisionClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "decision-service")
public interface DecisionClient {

    @GetMapping("/decision/decisions")
    List<DecisionClientResponse> getDecisions();
}
