package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.api.dto.ElectricityReadingRequest;
import com.citydisruptors.data_ingestion_service.config.kafka.events.ElectricityReadingEvent;
import com.citydisruptors.data_ingestion_service.config.kafka.producers.ElectricityReadingProducer;
import com.citydisruptors.data_ingestion_service.entity.DatasetImportState;
import com.citydisruptors.data_ingestion_service.repository.DatasetImportStateRepository;
import com.citydisruptors.data_ingestion_service.repository.ElectricityReadingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ElectricityIngestionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIngestSingleReadingAndPublishWhenInserted() {
        ElectricityReadingProducer producer = mock(ElectricityReadingProducer.class);
        ElectricityReadingRepository readingRepository = mock(ElectricityReadingRepository.class);
        DatasetImportStateRepository stateRepository = mock(DatasetImportStateRepository.class);
        ImportStatusCacheService statusCache = mock(ImportStatusCacheService.class);

        when(readingRepository.insertIgnoreDuplicate(
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class),
                anyDouble(),
                any(Instant.class)
        )).thenReturn(1);

        ElectricityIngestionService service = new ElectricityIngestionService(
                tempDir.toString(),
                producer,
                new LowCarbonLondonCsvMapper(),
                new SimpleMeterRegistry(),
                readingRepository,
                stateRepository,
                statusCache
        );

        ElectricityReadingRequest request = new ElectricityReadingRequest(
                "MAC000002",
                "Std",
                Instant.parse("2012-10-12T12:00:00Z"),
                0.663
        );

        ElectricityReadingEvent result = service.ingestSingle(request);

        assertThat(result.householdId()).isEqualTo("MAC000002");
        verify(readingRepository).insertIgnoreDuplicate(
                any(UUID.class),
                eq("manual-api"),
                eq("MAC000002"),
                eq("Std"),
                eq(Instant.parse("2012-10-12T12:00:00Z")),
                eq(0.663),
                any(Instant.class)
        );
        verify(producer).publish(any(ElectricityReadingEvent.class));
    }

    @Test
    void shouldNotPublishSingleReadingWhenDuplicate() {
        ElectricityReadingProducer producer = mock(ElectricityReadingProducer.class);
        ElectricityReadingRepository readingRepository = mock(ElectricityReadingRepository.class);
        DatasetImportStateRepository stateRepository = mock(DatasetImportStateRepository.class);
        ImportStatusCacheService statusCache = mock(ImportStatusCacheService.class);

        when(readingRepository.insertIgnoreDuplicate(
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class),
                anyDouble(),
                any(Instant.class)
        )).thenReturn(0);

        ElectricityIngestionService service = new ElectricityIngestionService(
                tempDir.toString(),
                producer,
                new LowCarbonLondonCsvMapper(),
                new SimpleMeterRegistry(),
                readingRepository,
                stateRepository,
                statusCache
        );

        ElectricityReadingRequest request = new ElectricityReadingRequest(
                "MAC000002",
                "Std",
                Instant.parse("2012-10-12T12:00:00Z"),
                0.663
        );

        service.ingestSingle(request);

        verify(producer, never()).publish(any());
    }

    @Test
    void shouldImportCsvBatchFromBeginningAndUpdateOffset() throws Exception {
        Path csv = tempDir.resolve("sample.csv");

        Files.writeString(csv, """
                LCLid,stdorToU,DateTime,KWH/hh (per half hour)
                MAC000002,Std,2012-10-12 00:30:00.0000000,0
                MAC000002,Std,2012-10-12 01:00:00.0000000,0.1
                MAC000002,Std,2012-10-12 01:30:00.0000000,0.2
                """);

        ElectricityReadingProducer producer = mock(ElectricityReadingProducer.class);
        ElectricityReadingRepository readingRepository = mock(ElectricityReadingRepository.class);
        DatasetImportStateRepository stateRepository = mock(DatasetImportStateRepository.class);
        ImportStatusCacheService statusCache = mock(ImportStatusCacheService.class);

        DatasetImportState state = new DatasetImportState("sample.csv");

        when(stateRepository.findByFileName("sample.csv")).thenReturn(Optional.of(state));
        when(stateRepository.save(any(DatasetImportState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(readingRepository.insertIgnoreDuplicate(
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class),
                anyDouble(),
                any(Instant.class)
        )).thenReturn(1);

        ElectricityIngestionService service = new ElectricityIngestionService(
                tempDir.toString(),
                producer,
                new LowCarbonLondonCsvMapper(),
                new SimpleMeterRegistry(),
                readingRepository,
                stateRepository,
                statusCache
        );

        var response = service.importCsv("sample.csv", 2);

        assertThat(response.importedRows()).isEqualTo(2);
        assertThat(response.skippedRows()).isEqualTo(0);
        assertThat(state.getNextRecordNumber()).isEqualTo(3);
        assertThat(state.isCompleted()).isFalse();

        verify(producer, times(2)).publish(any(ElectricityReadingEvent.class));
        verify(producer).flush();
        verify(statusCache).updateStatus("sample.csv", 2, 0, false);
    }

    @Test
    void shouldContinueImportFromSavedOffset() throws Exception {
        Path csv = tempDir.resolve("sample.csv");

        Files.writeString(csv, """
                LCLid,stdorToU,DateTime,KWH/hh (per half hour)
                MAC000002,Std,2012-10-12 00:30:00.0000000,0
                MAC000002,Std,2012-10-12 01:00:00.0000000,0.1
                MAC000002,Std,2012-10-12 01:30:00.0000000,0.2
                """);

        ElectricityReadingProducer producer = mock(ElectricityReadingProducer.class);
        ElectricityReadingRepository readingRepository = mock(ElectricityReadingRepository.class);
        DatasetImportStateRepository stateRepository = mock(DatasetImportStateRepository.class);
        ImportStatusCacheService statusCache = mock(ImportStatusCacheService.class);

        DatasetImportState state = new DatasetImportState("sample.csv");
        state.setNextRecordNumber(3);
        state.setImportedRows(2);

        when(stateRepository.findByFileName("sample.csv")).thenReturn(Optional.of(state));
        when(stateRepository.save(any(DatasetImportState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(readingRepository.insertIgnoreDuplicate(
                any(UUID.class),
                anyString(),
                anyString(),
                anyString(),
                any(Instant.class),
                anyDouble(),
                any(Instant.class)
        )).thenReturn(1);

        ElectricityIngestionService service = new ElectricityIngestionService(
                tempDir.toString(),
                producer,
                new LowCarbonLondonCsvMapper(),
                new SimpleMeterRegistry(),
                readingRepository,
                stateRepository,
                statusCache
        );

        var response = service.importCsv("sample.csv", 10);

        assertThat(response.importedRows()).isEqualTo(1);
        assertThat(state.getNextRecordNumber()).isEqualTo(4);
        assertThat(state.isCompleted()).isTrue();

        verify(producer, times(1)).publish(any(ElectricityReadingEvent.class));
    }

    @Test
    void shouldReturnNoFileWhenAllDatasetsCompleted() throws Exception {
        Files.writeString(tempDir.resolve("sample.csv"), """
                LCLid,stdorToU,DateTime,KWH/hh (per half hour)
                MAC000002,Std,2012-10-12 00:30:00.0000000,0
                """);

        ElectricityReadingProducer producer = mock(ElectricityReadingProducer.class);
        ElectricityReadingRepository readingRepository = mock(ElectricityReadingRepository.class);
        DatasetImportStateRepository stateRepository = mock(DatasetImportStateRepository.class);
        ImportStatusCacheService statusCache = mock(ImportStatusCacheService.class);

        DatasetImportState state = new DatasetImportState("sample.csv");
        state.setCompleted(true);

        when(stateRepository.findByFileName("sample.csv")).thenReturn(Optional.of(state));

        ElectricityIngestionService service = new ElectricityIngestionService(
                tempDir.toString(),
                producer,
                new LowCarbonLondonCsvMapper(),
                new SimpleMeterRegistry(),
                readingRepository,
                stateRepository,
                statusCache
        );

        var response = service.importNextAvailableFile(100);

        assertThat(response.fileName()).isEqualTo("NO_FILE_AVAILABLE");
        assertThat(response.importedRows()).isEqualTo(0);
        verifyNoInteractions(producer);
    }
}