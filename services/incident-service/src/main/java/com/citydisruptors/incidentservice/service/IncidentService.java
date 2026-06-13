package com.citydisruptors.incidentservice.service;

import com.citydisruptors.incidentservice.api.dto.CreateIncidentRequest;
import com.citydisruptors.incidentservice.api.dto.IncidentResponse;
import com.citydisruptors.incidentservice.api.dto.UpdateIncidentStatusRequest;
import com.citydisruptors.incidentservice.config.kafka.events.IncidentCreatedEvent;
import com.citydisruptors.incidentservice.config.kafka.events.IncidentUpdatedEvent;
import com.citydisruptors.incidentservice.config.kafka.producers.IncidentCreatedProducer;
import com.citydisruptors.incidentservice.config.kafka.producers.IncidentUpdatedProducer;
import com.citydisruptors.incidentservice.entity.Incident;
import com.citydisruptors.incidentservice.entity.IncidentStatus;
import com.citydisruptors.incidentservice.entity.IncidentType;
import com.citydisruptors.incidentservice.entity.mapper.IncidentMapper;
import com.citydisruptors.incidentservice.exception.IncidentNotFoundException;
import com.citydisruptors.incidentservice.repository.IncidentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    @Autowired
    private final IncidentRepository repository;

    private final Counter created;
    private final Counter updated;
    private final Counter incidentsResolved;
    private final Counter creationFailed;
    private final Counter updateFailed;

    private final IncidentCreatedProducer incidentCreatedProducer;
    private final IncidentUpdatedProducer incidentUpdatedProducer;

    public IncidentService(IncidentRepository repository, MeterRegistry registry, IncidentCreatedProducer incidentCreatedProducer, IncidentUpdatedProducer incidentUpdatedProducer) {
        this.repository = repository;
        this.created = registry.counter("incidents.created.success");
        this.updated = registry.counter("incidents.updated.success");
        this.incidentsResolved = registry.counter("incidents.resolved");
        this.creationFailed = registry.counter("incidents.created.failure");
        this.updateFailed = registry.counter("incidents.updated.failure");

        this.incidentCreatedProducer = incidentCreatedProducer;
        this.incidentUpdatedProducer = incidentUpdatedProducer;
    }

    public IncidentResponse create(CreateIncidentRequest request) {
        try {
            log.info("Started creating an incident");
            Incident incident = IncidentMapper.toEntity(request);
            Incident saved = repository.save(incident);

            created.increment();

            incidentCreatedProducer.publish(IncidentCreatedEvent.of(saved));
            return IncidentMapper.toResponse(repository.save(incident));
        }
        catch (Exception ex) {
            creationFailed.increment();
            log.error("Exception occurred while saving incident: ", ex.getMessage());
            return null;
        }
    }

    public IncidentResponse getById(UUID id) {
        log.info("Incident requested by ID");
        return repository.findById(id)
                .map(IncidentMapper::toResponse)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with id: " + id));
    }

    public List<IncidentResponse> getAll(IncidentStatus status, IncidentType type) {
        log.info("All incident queried by status or type");
        return repository.findAll().stream().map(IncidentMapper::toResponse).toList();
    }

    public IncidentResponse updateStatus(UUID id, UpdateIncidentStatusRequest request) {
        log.info("Requesting incident status update to {}", request.status());
        IncidentStatus prevStatus = null;
        try {
            Incident incident = repository.findById(id)
                    .orElseThrow(() -> new IncidentNotFoundException("Incident not found with id: " + id));
            prevStatus = incident.getIncidentStatus();

            incident.setIncidentStatus(request.status());

            Incident updatedEntity = repository.save(incident);

            updated.increment();
            if (request.status().equals(IncidentStatus.RESOLVED)) {
                incidentsResolved.increment();
            }

            incidentUpdatedProducer.publish(IncidentUpdatedEvent.of(updatedEntity));

            return IncidentMapper.toResponse(repository.save(incident));
        } catch (Exception ex) {
            updateFailed.increment();
            log.error("Failed to update status from {} to {} for incident with id {}", prevStatus, request.status(), id.toString());
            return null;
        }
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new IncidentNotFoundException("Incident not found with id: " + id);
        repository.deleteById(id);
    }
}