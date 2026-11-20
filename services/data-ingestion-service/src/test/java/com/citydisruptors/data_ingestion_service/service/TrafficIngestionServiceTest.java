package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.api.dto.ImportResponse;
import com.citydisruptors.data_ingestion_service.api.dto.TrafficReadingRequest;
import com.citydisruptors.data_ingestion_service.config.kafka.events.TrafficReadingEvent;
import com.citydisruptors.data_ingestion_service.config.kafka.producers.TrafficReadingProducer;
import com.citydisruptors.data_ingestion_service.entity.DatasetImportState;
import com.citydisruptors.data_ingestion_service.repository.DatasetImportStateRepository;
import com.citydisruptors.data_ingestion_service.repository.TrafficReadingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficIngestionServiceTest {

    private static final String HEADER = "DateTime,Junction,Vehicles,ID\n";

    @TempDir
    Path tempDir;

    private TrafficReadingProducer producer;
    private TrafficReadingRepository readingRepository;
    private Map<String, DatasetImportState> states;
    private TrafficIngestionService service;

    @BeforeEach
    void setUp() {
        producer = mock(TrafficReadingProducer.class);
        readingRepository = mock(TrafficReadingRepository.class);
        states = new HashMap<>();

        DatasetImportStateRepository stateRepository = mock(DatasetImportStateRepository.class);
        when(stateRepository.findByFileName(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(states.get(invocation.<String>getArgument(0))));
        when(stateRepository.save(any(DatasetImportState.class))).thenAnswer(invocation -> {
            DatasetImportState state = invocation.getArgument(0);
            states.put(state.getFileName(), state);
            return state;
        });

        service = new TrafficIngestionService(
                tempDir.toString(),
                producer,
                new TrafficCsvMapper(),
                readingRepository,
                stateRepository,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void publishesEveryRowAndMarksFileCompleted() throws IOException {
        writeCsv("traffic.csv", "2015-11-01 00:00:00,1,15,1", "2015-11-01 01:00:00,1,13,2");

        ImportResponse response = service.importCsv("traffic.csv", 100);

        assertThat(response.importedRows()).isEqualTo(2);
        assertThat(published()).extracting(TrafficReadingEvent::vehicles).containsExactly(15, 13);
        assertThat(published().getFirst().junctionId()).isEqualTo("1");
        assertThat(published().getFirst().readingTimestamp()).isEqualTo(Instant.parse("2015-11-01T00:00:00Z"));
        verify(readingRepository, times(2)).insertIgnoreDuplicate(any(TrafficReadingEvent.class));

        DatasetImportState state = states.get("traffic/traffic.csv");
        assertThat(state.isCompleted()).isTrue();
        assertThat(state.getNextRecordNumber()).isEqualTo(3);
    }

    @Test
    void continuesFromTheCursorInBatches() throws IOException {
        writeCsv("traffic.csv",
                "2015-11-01 00:00:00,1,10,1", "2015-11-01 01:00:00,1,11,2", "2015-11-01 02:00:00,1,12,3");

        service.importCsv("traffic.csv", 2);
        assertThat(states.get("traffic/traffic.csv").isCompleted()).isFalse();

        service.importCsv("traffic.csv", 2);

        assertThat(published()).extracting(TrafficReadingEvent::vehicles).containsExactly(10, 11, 12);
        assertThat(states.get("traffic/traffic.csv").isCompleted()).isTrue();
        assertThat(service.importCsv("traffic.csv", 2).importedRows()).isZero();
    }

    @Test
    void skipsInvalidRowsWithoutStopping() throws IOException {
        writeCsv("traffic.csv",
                "2015-11-01 00:00:00,1,10,1", "not-a-date,1,11,2", "2015-11-01 02:00:00,1,-4,3",
                "2015-11-01 03:00:00,1,12,4");

        ImportResponse response = service.importCsv("traffic.csv", 100);

        assertThat(response.importedRows()).isEqualTo(2);
        assertThat(response.skippedRows()).isEqualTo(2);
        assertThat(published()).extracting(TrafficReadingEvent::vehicles).containsExactly(10, 12);
    }

    @Test
    void failedPublishStopsTheBatchAndTheRowIsPublishedOnTheNextRun() throws IOException {
        writeCsv("traffic.csv",
                "2015-11-01 00:00:00,1,10,1", "2015-11-01 01:00:00,1,11,2", "2015-11-01 02:00:00,1,12,3");

        doNothing().doThrow(new KafkaException("broker down")).when(producer).publish(any(TrafficReadingEvent.class));

        assertThatThrownBy(() -> service.importCsv("traffic.csv", 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("record 2");

        DatasetImportState state = states.get("traffic/traffic.csv");
        assertThat(state.getNextRecordNumber()).isEqualTo(2);
        assertThat(state.isCompleted()).isFalse();

        doNothing().when(producer).publish(any(TrafficReadingEvent.class));
        ImportResponse retry = service.importCsv("traffic.csv", 100);

        assertThat(retry.importedRows()).isEqualTo(2);
        assertThat(published()).extracting(TrafficReadingEvent::vehicles).containsExactly(10, 11, 11, 12);
        assertThat(state.isCompleted()).isTrue();
    }

    @Test
    void failedInsertDoesNotPublishOrAdvance() throws IOException {
        writeCsv("traffic.csv", "2015-11-01 00:00:00,1,10,1");
        doThrow(new IllegalStateException("database down"))
                .when(readingRepository).insertIgnoreDuplicate(any(TrafficReadingEvent.class));

        assertThatThrownBy(() -> service.importCsv("traffic.csv", 100)).isInstanceOf(IllegalStateException.class);

        verify(producer, never()).publish(any(TrafficReadingEvent.class));
        assertThat(states.get("traffic/traffic.csv").getNextRecordNumber()).isEqualTo(1);
    }

    @Test
    void rejectsFilesOutsideTheDatasetFolder() throws IOException {
        writeCsv("traffic.csv", "2015-11-01 00:00:00,1,10,1");
        Files.writeString(tempDir.resolveSibling("secret.csv"), HEADER);

        assertThatThrownBy(() -> service.importCsv("../secret.csv", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.importCsv("missing.csv", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importsFilesInOrderAndReportsWhenNothingIsLeft() throws IOException {
        writeCsv("b.csv", "2015-11-01 00:00:00,2,20,1");
        writeCsv("a.csv", "2015-11-01 00:00:00,1,10,1");
        Files.writeString(tempDir.resolve("notes.txt"), "ignored");

        assertThat(service.availableDatasets()).containsExactly("a.csv", "b.csv");
        assertThat(service.importNextAvailableFile(10).fileName()).isEqualTo("a.csv");
        assertThat(service.importNextAvailableFile(10).fileName()).isEqualTo("b.csv");
        assertThat(service.importNextAvailableFile(10).fileName()).isEqualTo("NO_FILE_AVAILABLE");
    }

    @Test
    void sameReadingAlwaysGetsTheSameEventId() {
        TrafficReadingRequest request = new TrafficReadingRequest("3", Instant.parse("2017-03-06T08:00:00Z"), 42);

        TrafficReadingEvent first = service.ingestSingle(request);
        TrafficReadingEvent second = service.ingestSingle(request);

        assertThat(first.eventId()).isEqualTo(second.eventId());
        assertThat(first.source()).isEqualTo("manual-api");
        verify(producer, times(2)).publish(any(TrafficReadingEvent.class));
    }

    private List<TrafficReadingEvent> published() {
        ArgumentCaptor<TrafficReadingEvent> captor = ArgumentCaptor.forClass(TrafficReadingEvent.class);
        verify(producer, org.mockito.Mockito.atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    private void writeCsv(String fileName, String... rows) throws IOException {
        Files.writeString(tempDir.resolve(fileName), HEADER + String.join("\n", rows) + "\n");
    }
}
