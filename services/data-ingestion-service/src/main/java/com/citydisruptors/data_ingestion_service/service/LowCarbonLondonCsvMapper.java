package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.config.kafka.events.ElectricityReadingEvent;
import com.citydisruptors.data_ingestion_service.entity.ElectricityReadingEntity;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class LowCarbonLondonCsvMapper {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSS");

    public ElectricityReadingEvent map(CSVRecord record) {
        String householdId = record.get("LCLid").trim();
        String tariffType = record.get("stdorToU").trim();
        String dateTimeRaw = record.get("DateTime").trim();
        String kwhRaw = record.get("KWH/hh (per half hour)").trim();

        LocalDateTime localDateTime = LocalDateTime.parse(dateTimeRaw, FORMATTER);
        Instant timestamp = localDateTime.toInstant(ZoneOffset.UTC);

        Double kwh = Double.parseDouble(kwhRaw);

        return ElectricityReadingEvent.of(
                "low-carbon-london",
                householdId,
                tariffType,
                timestamp,
                kwh
        );
    }

    public ElectricityReadingEntity toEntity(ElectricityReadingEvent event) {
        return new ElectricityReadingEntity(
                UUID.fromString(event.eventId()),
                event.source(),
                event.householdId(),
                event.tariffType(),
                event.readingTimestamp(),
                event.kwh(),
                event.ingestedAt()
        );
    }
}
