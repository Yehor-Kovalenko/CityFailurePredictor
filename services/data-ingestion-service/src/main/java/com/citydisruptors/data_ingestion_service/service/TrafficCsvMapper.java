package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.config.kafka.events.TrafficReadingEvent;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Maps a row of the junction traffic dataset (DateTime, Junction, Vehicles, ID).
 */
@Component
public class TrafficCsvMapper {

    static final String SOURCE = "traffic-junctions";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TrafficReadingEvent map(CSVRecord record) {
        String junctionId = record.get("Junction").trim();
        LocalDateTime dateTime = LocalDateTime.parse(record.get("DateTime").trim(), FORMATTER);
        int vehicles = Integer.parseInt(record.get("Vehicles").trim());

        if (junctionId.isEmpty()) {
            throw new IllegalArgumentException("Junction is empty");
        }

        if (vehicles < 0) {
            throw new IllegalArgumentException("Vehicles must not be negative: " + vehicles);
        }

        return TrafficReadingEvent.of(
                SOURCE,
                junctionId,
                dateTime.toInstant(ZoneOffset.UTC),
                vehicles
        );
    }
}
