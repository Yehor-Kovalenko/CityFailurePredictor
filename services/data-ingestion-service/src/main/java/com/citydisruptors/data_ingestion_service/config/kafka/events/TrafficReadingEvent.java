package com.citydisruptors.data_ingestion_service.config.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record TrafficReadingEvent(
        String eventId,
        String source,
        String junctionId,
        Instant readingTimestamp,
        Integer vehicles,
        Instant ingestedAt
) {
    public static TrafficReadingEvent of(
            String source,
            String junctionId,
            Instant readingTimestamp,
            Integer vehicles
    ) {
        String idBase = source + ":traffic:" + junctionId + ":" + readingTimestamp;
        return new TrafficReadingEvent(
                UUID.nameUUIDFromBytes(idBase.getBytes()).toString(),
                source,
                junctionId,
                readingTimestamp,
                vehicles,
                Instant.now()
        );
    }
}
