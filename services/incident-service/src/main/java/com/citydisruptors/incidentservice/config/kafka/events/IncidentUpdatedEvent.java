package com.citydisruptors.incidentservice.config.kafka.events;

import com.citydisruptors.incidentservice.entity.Incident;
import com.citydisruptors.incidentservice.entity.IncidentStatus;
import com.citydisruptors.incidentservice.entity.IncidentType;

import java.time.Instant;
import java.util.UUID;

public record IncidentUpdatedEvent(
        String eventId,
        String incidentTitle,
        String incidentSummary,
        String incidentId,
        String incidentType,
        String incidentStatus,
        Instant createdTimestamp
) {
    public static IncidentUpdatedEvent of(
            Incident incident
    ) {
        String idBase = incident.getIncidentTitle() + ":" + incident.getId() + ":" + Instant.now();
        return new IncidentUpdatedEvent(
                UUID.nameUUIDFromBytes(idBase.getBytes()).toString(),
                incident.getIncidentTitle(),
                incident.getIncidentSummary(),
                incident.getId().toString(),
                incident.getIncidentType().toString(),
                incident.getIncidentStatus().toString(),
                Instant.now()
        );
    }
}
