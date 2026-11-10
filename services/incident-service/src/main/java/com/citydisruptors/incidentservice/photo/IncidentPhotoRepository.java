package com.citydisruptors.incidentservice.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

public interface IncidentPhotoRepository extends JpaRepository<IncidentPhoto, UUID> {

    @Query("select p.incidentId from IncidentPhoto p")
    Set<UUID> findAllIncidentIds();
}
