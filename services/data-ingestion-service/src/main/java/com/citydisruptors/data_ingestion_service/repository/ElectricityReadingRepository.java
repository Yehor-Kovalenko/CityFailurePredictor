package com.citydisruptors.data_ingestion_service.repository;

import com.citydisruptors.data_ingestion_service.entity.ElectricityReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ElectricityReadingRepository extends JpaRepository<ElectricityReadingEntity, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO electricity_readings
            (event_id, source, household_id, tariff_type, reading_timestamp, kwh, ingested_at)
            VALUES
            (:eventId, :source, :householdId, :tariffType, :readingTimestamp, :kwh, :ingestedAt)
            ON CONFLICT (event_id, reading_timestamp) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoreDuplicate(
            @Param("eventId") UUID eventId,
            @Param("source") String source,
            @Param("householdId") String householdId,
            @Param("tariffType") String tariffType,
            @Param("readingTimestamp") Instant readingTimestamp,
            @Param("kwh") Double kwh,
            @Param("ingestedAt") Instant ingestedAt
    );
}
