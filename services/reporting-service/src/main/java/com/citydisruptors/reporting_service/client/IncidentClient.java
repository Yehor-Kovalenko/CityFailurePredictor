package com.citydisruptors.reporting_service.client;

import com.citydisruptors.reporting_service.client.dto.IncidentClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "incident-service")
public interface IncidentClient {

    @GetMapping("/incidents/all")
    List<IncidentClientResponse> getIncidents();
}
