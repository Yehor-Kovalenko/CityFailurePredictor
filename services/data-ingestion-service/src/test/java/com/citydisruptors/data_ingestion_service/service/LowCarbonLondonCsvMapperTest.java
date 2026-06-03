package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.config.kafka.events.ElectricityReadingEvent;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LowCarbonLondonCsvMapperTest {

    private final LowCarbonLondonCsvMapper mapper = new LowCarbonLondonCsvMapper();

    @Test
    void shouldMapCsvRecordToElectricityReadingEvent() throws Exception {
        String csv = """
                LCLid,stdorToU,DateTime,KWH/hh (per half hour)
                MAC000002,Std,2012-10-12 12:00:00.0000000,0.663
                """;

        CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build()
                .parse(new StringReader(csv));

        var record = parser.iterator().next();

        ElectricityReadingEvent event = mapper.map(record);

        assertThat(event.source()).isEqualTo("low-carbon-london");
        assertThat(event.householdId()).isEqualTo("MAC000002");
        assertThat(event.tariffType()).isEqualTo("Std");
        assertThat(event.kwh()).isEqualTo(0.663);
        assertThat(event.readingTimestamp()).isEqualTo(Instant.parse("2012-10-12T12:00:00Z"));
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.ingestedAt()).isNotNull();
    }
}