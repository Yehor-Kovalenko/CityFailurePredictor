package com.citydisruptors.incidentservice.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(nullable = false)
    private String incidentTitle;
    private String incidentSummary;

    @Embedded
    private Coordinates coordinates;

    @Enumerated(EnumType.STRING)
    private IncidentType incidentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus incidentStatus = IncidentStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    private Instant lastUpdated;

    @PrePersist
    protected void onCreate() {
        this.timestamp = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastUpdated = Instant.now();
    }

    public Incident() {
    }

    public Incident(String incidentTitle, String incidentSummary, Coordinates coordinates, IncidentType incidentType) {
        this.incidentTitle = incidentTitle;
        this.incidentSummary = incidentSummary;
        this.coordinates = coordinates;
        this.incidentType = incidentType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIncidentTitle() {
        return incidentTitle;
    }

    public void setIncidentTitle(String incidentTitle) {
        this.incidentTitle = incidentTitle;
    }

    public String getIncidentSummary() {
        return incidentSummary;
    }

    public void setIncidentSummary(String incidentSummary) {
        this.incidentSummary = incidentSummary;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public void setIncidentType(IncidentType incidentType) {
        this.incidentType = incidentType;
    }

    public IncidentStatus getIncidentStatus() {
        return incidentStatus;
    }

    public void setIncidentStatus(IncidentStatus incidentStatus) {
        this.incidentStatus = incidentStatus;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }


}
