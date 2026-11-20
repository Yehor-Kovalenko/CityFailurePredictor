package com.citydisruptors.data_ingestion_service.repository;

import com.citydisruptors.data_ingestion_service.config.kafka.events.TrafficReadingEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class TrafficReadingRepository {

    private static final String INSERT_IGNORE_DUPLICATE = """
            INSERT INTO traffic_readings
            (event_id, source, junction_id, reading_timestamp, vehicles, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id, reading_timestamp) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public TrafficReadingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertIgnoreDuplicate(TrafficReadingEvent event) {
        return jdbcTemplate.update(
                INSERT_IGNORE_DUPLICATE,
                UUID.fromString(event.eventId()),
                event.source(),
                event.junctionId(),
                event.readingTimestamp().atOffset(ZoneOffset.UTC),
                event.vehicles(),
                event.ingestedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
