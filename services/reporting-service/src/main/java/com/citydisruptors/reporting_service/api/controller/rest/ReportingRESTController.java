package com.citydisruptors.reporting_service.api.controller.rest;

import com.citydisruptors.reporting_service.api.dto.GenerateReportRequest;
import com.citydisruptors.reporting_service.api.dto.ReportResponse;
import com.citydisruptors.reporting_service.service.ReportingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
public class ReportingRESTController {

    private final ReportingService service;

    public ReportingRESTController(ReportingService service) {
        this.service = service;
    }

    @GetMapping("/test")
    public String test() {
        return "REPORTING SERVICE OK";
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse generateReport(@RequestBody @Valid GenerateReportRequest request) {
        return ReportResponse.from(service.generateReport(request));
    }

    @GetMapping
    public List<ReportResponse> getReports() {
        return service.getReports()
                .stream()
                .map(ReportResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ReportResponse getReport(@PathVariable UUID id) {
        return ReportResponse.from(service.getReport(id));
    }

    @GetMapping("/summary")
    public Map<String, Object> getSystemSummary() {
        return service.getCachedSystemSummary();
    }
}
