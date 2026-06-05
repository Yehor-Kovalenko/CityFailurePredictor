package com.citydisruptors.incidentservice.api.controller.rest;

import com.citydisruptors.incidentservice.api.dto.CreateIncidentRequest;
import com.citydisruptors.incidentservice.api.dto.IncidentResponse;
import com.citydisruptors.incidentservice.api.dto.UpdateIncidentStatusRequest;
import com.citydisruptors.incidentservice.entity.IncidentStatus;
import com.citydisruptors.incidentservice.entity.IncidentType;
import com.citydisruptors.incidentservice.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/incident-service")
public class IncidentRESTController {

    @Autowired
    private final IncidentService service;

    public IncidentRESTController(IncidentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(@RequestBody @Valid CreateIncidentRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public IncidentResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/all")
    public List<IncidentResponse> getAll(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentType type
    ) {
        return service.getAll(status, type);
    }

    @PatchMapping("/{id}/status")
    public IncidentResponse updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateIncidentStatusRequest request
    ) {
        return service.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/statuses")
    public ResponseEntity<?> getStatuses() {
        return ResponseEntity.ok(IncidentStatus.values());
    }
}