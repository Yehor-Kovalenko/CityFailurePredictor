package com.citydisruptors.data_ingestion_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "electricity_readings")
public class ElectricityReadingEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String source;

    @Column(name = "household_id", nullable = false)
    private String householdId;

    @Column(name = "tariff_type", nullable = false)
    private String tariffType;

    @Column(name = "reading_timestamp", nullable = false)
    private Instant readingTimestamp;

    @Column(nullable = false)
    private Double kwh;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected ElectricityReadingEntity() {
    }

    public ElectricityReadingEntity(UUID eventId, String source, String householdId, String tariffType,
                                    Instant readingTimestamp, Double kwh, Instant ingestedAt) {
        this.eventId = eventId;
        this.source = source;
        this.householdId = householdId;
        this.tariffType = tariffType;
        this.readingTimestamp = readingTimestamp;
        this.kwh = kwh;
        this.ingestedAt = ingestedAt;
    }
}