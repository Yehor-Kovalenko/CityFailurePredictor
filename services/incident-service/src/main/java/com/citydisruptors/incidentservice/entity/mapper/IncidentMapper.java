package com.citydisruptors.incidentservice.entity.mapper;

import com.citydisruptors.incidentservice.api.dto.CreateIncidentRequest;
import com.citydisruptors.incidentservice.api.dto.IncidentResponse;
import com.citydisruptors.incidentservice.entity.Incident;

public class IncidentMapper {

    public static Incident toEntity(CreateIncidentRequest request) {
        return new Incident(request.incidentTitle(), request.incidentSummary(), request.coordinates(), request.incidentType());
    }

    public static IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getIncidentTitle(),
                incident.getIncidentSummary(),
                incident.getCoordinates(),
                incident.getIncidentType(),
                incident.getIncidentStatus(),
                incident.getCreatedTimestamp(),
                incident.getLastUpdatedTimestamp()
        );
    }
}
