package com.citydisruptors.incidentservice.repository;

import com.citydisruptors.incidentservice.entity.Coordinates;
import com.citydisruptors.incidentservice.entity.Incident;
import com.citydisruptors.incidentservice.entity.IncidentStatus;
import com.citydisruptors.incidentservice.entity.IncidentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByIncidentStatus(IncidentStatus status);

    List<Incident> findByIncidentType(IncidentType type);

    List<Incident> findByCoordinates(Coordinates coordinates);
}

