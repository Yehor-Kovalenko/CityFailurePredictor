package com.citydisruptors.incidentservice.service;

import com.citydisruptors.incidentservice.api.dto.CreateIncidentRequest;
import com.citydisruptors.incidentservice.api.dto.IncidentResponse;
import com.citydisruptors.incidentservice.api.dto.UpdateIncidentStatusRequest;
import com.citydisruptors.incidentservice.entity.Incident;
import com.citydisruptors.incidentservice.entity.IncidentStatus;
import com.citydisruptors.incidentservice.entity.IncidentType;
import com.citydisruptors.incidentservice.entity.mapper.IncidentMapper;
import com.citydisruptors.incidentservice.exception.IncidentNotFoundException;
import com.citydisruptors.incidentservice.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class IncidentService {

    @Autowired
    private final IncidentRepository repository;

    public IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    public IncidentResponse create(CreateIncidentRequest request) {
        Incident incident = IncidentMapper.toEntity(request);
        return IncidentMapper.toResponse(repository.save(incident));
    }

    public IncidentResponse getById(UUID id) {
        return repository.findById(id)
                .map(IncidentMapper::toResponse)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with id: " + id));
    }

    public List<IncidentResponse> getAll(IncidentStatus status, IncidentType type) {
        return repository.findAll().stream().map(IncidentMapper::toResponse).toList();
    }

    public IncidentResponse updateStatus(UUID id, UpdateIncidentStatusRequest request) {
        Incident incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with id: " + id));
        incident.setIncidentStatus(request.status());
        return IncidentMapper.toResponse(repository.save(incident));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new IncidentNotFoundException("Incident not found with id: " + id);
        repository.deleteById(id);
    }
}