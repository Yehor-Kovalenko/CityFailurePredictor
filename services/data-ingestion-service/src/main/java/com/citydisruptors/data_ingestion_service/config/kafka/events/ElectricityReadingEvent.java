package com.citydisruptors.data_ingestion_service.config.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record ElectricityReadingEvent(
        String eventId,
        String source,
        String householdId,
        String tariffType,
        Instant readingTimestamp,
        Double kwh,
        Instant ingestedAt
) {
    public static ElectricityReadingEvent of(
            String source,
            String householdId,
            String tariffType,
            Instant readingTimestamp,
            Double kwh
    ) {
        String idBase = source + ":" + householdId + ":" + readingTimestamp;
        return new ElectricityReadingEvent(
                UUID.nameUUIDFromBytes(idBase.getBytes()).toString(),
                source,
                householdId,
                tariffType,
                readingTimestamp,
                kwh,
                Instant.now()
        );
    }
}
