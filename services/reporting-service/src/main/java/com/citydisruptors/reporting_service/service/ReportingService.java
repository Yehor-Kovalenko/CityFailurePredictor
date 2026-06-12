package com.citydisruptors.reporting_service.service;

import com.citydisruptors.reporting_service.api.dto.GenerateReportRequest;
import com.citydisruptors.reporting_service.client.DecisionClient;
import com.citydisruptors.reporting_service.client.IncidentClient;
import com.citydisruptors.reporting_service.client.dto.DecisionClientResponse;
import com.citydisruptors.reporting_service.client.dto.IncidentClientResponse;
import com.citydisruptors.reporting_service.entity.*;
import com.citydisruptors.reporting_service.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportingService {

    private final ReportRepository repository;
    private final IncidentClient incidentClient;
    private final DecisionClient decisionClient;
    private final ObjectMapper objectMapper;

    private final Counter reportsGenerated;
    private final Counter reportsFailed;
    private final Counter incidentClientFallbacks;
    private final Counter decisionClientFallbacks;
    private final Timer reportGenerationTimer;

    public ReportingService(
            ReportRepository repository,
            IncidentClient incidentClient,
            DecisionClient decisionClient,
            ObjectMapper objectMapper,
            MeterRegistry registry
    ) {
        this.repository = repository;
        this.incidentClient = incidentClient;
        this.decisionClient = decisionClient;
        this.objectMapper = objectMapper;

        this.reportsGenerated = registry.counter("reporting.reports.generated");
        this.reportsFailed = registry.counter("reporting.reports.failed");
        this.incidentClientFallbacks = registry.counter("reporting.client.fallbacks", "client", "incident-service");
        this.decisionClientFallbacks = registry.counter("reporting.client.fallbacks", "client", "decision-service");
        this.reportGenerationTimer = Timer.builder("reporting.reports.generation.duration")
                .description("Report generation duration")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Transactional
    public Report generateReport(GenerateReportRequest request) {
        return reportGenerationTimer.record(() -> {
            try {
                Report report = switch (request.type()) {
                    case SYSTEM_SUMMARY -> generateSystemSummary(request);
                    case INCIDENT_SUMMARY -> generateIncidentSummary(request);
                    case DECISION_SUMMARY -> generateDecisionSummary(request);
                };

                reportsGenerated.increment();
                return repository.save(report);

            } catch (Exception ex) {
                reportsFailed.increment();
                throw new IllegalStateException("Report generation failed", ex);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<Report> getReports() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Report getReport(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));
    }

    @Cacheable(value = "reporting-summary", key = "'system-summary'")
    public Map<String, Object> getCachedSystemSummary() {
        List<IncidentClientResponse> incidents = getIncidentsSafely();
        List<DecisionClientResponse> decisions = getDecisionsSafely();

        return buildSummaryContent(incidents, decisions);
    }

    private Report generateSystemSummary(GenerateReportRequest request) throws Exception {
        List<IncidentClientResponse> incidents = getIncidentsSafely();
        List<DecisionClientResponse> decisions = getDecisionsSafely();

        Map<String, Object> content = buildSummaryContent(incidents, decisions);

        return new Report(
                UUID.randomUUID(),
                ReportType.SYSTEM_SUMMARY,
                request.format(),
                ReportStatus.GENERATED,
                "System summary report",
                objectMapper.writeValueAsString(content),
                Instant.now()
        );
    }

    private Report generateIncidentSummary(GenerateReportRequest request) throws Exception {
        List<IncidentClientResponse> incidents = getIncidentsSafely();

        Map<String, Object> content = Map.of(
                "generatedAt", Instant.now().toString(),
                "sourceService", "incident-service",
                "incidentsCount", incidents.size(),
                "incidents", incidents
        );

        return new Report(
                UUID.randomUUID(),
                ReportType.INCIDENT_SUMMARY,
                request.format(),
                ReportStatus.GENERATED,
                "Incident summary report",
                objectMapper.writeValueAsString(content),
                Instant.now()
        );
    }

    private Report generateDecisionSummary(GenerateReportRequest request) throws Exception {
        List<DecisionClientResponse> decisions = getDecisionsSafely();

        Map<String, Object> content = Map.of(
                "generatedAt", Instant.now().toString(),
                "sourceService", "decision-service",
                "decisionsCount", decisions.size(),
                "decisions", decisions
        );

        return new Report(
                UUID.randomUUID(),
                ReportType.DECISION_SUMMARY,
                request.format(),
                ReportStatus.GENERATED,
                "Decision summary report",
                objectMapper.writeValueAsString(content),
                Instant.now()
        );
    }

    private Map<String, Object> buildSummaryContent(
            List<IncidentClientResponse> incidents,
            List<DecisionClientResponse> decisions
    ) {
        long highRiskDecisions = decisions.stream()
                .filter(d -> d.riskLevel() == RiskLevel.HIGH || d.riskLevel() == RiskLevel.CRITICAL)
                .count();

        long createdAlerts = decisions.stream()
                .filter(d -> d.alertId() != null)
                .count();

        long resolvedIncidents = incidents.stream()
                .filter(i -> i.status() == IncidentStatus.RESOLVED)
                .count();

        return Map.of(
                "generatedAt", Instant.now().toString(),
                "incidentsCount", incidents.size(),
                "resolvedIncidents", resolvedIncidents,
                "decisionsCount", decisions.size(),
                "highRiskDecisions", highRiskDecisions,
                "createdAlerts", createdAlerts,
                "sourceServices", List.of("incident-service", "decision-service")
        );
    }

    @Retry(name = "incident-service")
    @CircuitBreaker(name = "incident-service", fallbackMethod = "fallbackIncidents")
    public List<IncidentClientResponse> getIncidentsSafely() {
        return incidentClient.getIncidents();
    }

    @Retry(name = "decision-service")
    @CircuitBreaker(name = "decision-service", fallbackMethod = "fallbackDecisions")
    public List<DecisionClientResponse> getDecisionsSafely() {
        return decisionClient.getDecisions();
    }

    private List<IncidentClientResponse> fallbackIncidents(Throwable ex) {
        incidentClientFallbacks.increment();
        return List.of();
    }

    private List<DecisionClientResponse> fallbackDecisions(Throwable ex) {
        decisionClientFallbacks.increment();
        return List.of();
    }
}
