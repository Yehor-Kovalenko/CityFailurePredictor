package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.api.dto.ImportResponse;
import com.citydisruptors.data_ingestion_service.api.dto.TrafficReadingRequest;
import com.citydisruptors.data_ingestion_service.config.kafka.events.TrafficReadingEvent;
import com.citydisruptors.data_ingestion_service.config.kafka.producers.TrafficReadingProducer;
import com.citydisruptors.data_ingestion_service.entity.DatasetImportState;
import com.citydisruptors.data_ingestion_service.repository.DatasetImportStateRepository;
import com.citydisruptors.data_ingestion_service.repository.TrafficReadingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Replays the junction traffic dataset as traffic.readings.raw events.
 *
 * <p>The import cursor only moves past a row once its event was confirmed by
 * Kafka. If the broker or the database fails, the batch stops, the cursor
 * stays on the failed row and the next call starts from it. A row can
 * therefore be published twice, never zero times; consumers treat a repeated
 * (junction, timestamp) as the same reading.</p>
 */
@Service
public class TrafficIngestionService {

    static final String STATE_PREFIX = "traffic/";
    static final String NO_FILE_AVAILABLE = "NO_FILE_AVAILABLE";

    private static final Logger log = LoggerFactory.getLogger(TrafficIngestionService.class);

    private final Path datasetBasePath;
    private final TrafficReadingProducer producer;
    private final TrafficCsvMapper mapper;
    private final TrafficReadingRepository readingRepository;
    private final DatasetImportStateRepository stateRepository;

    private final Counter received;
    private final Counter skipped;
    private final Counter importFailed;

    public TrafficIngestionService(
            @Value("${app.dataset.traffic-base-path:/data/traffic}") String datasetBasePath,
            TrafficReadingProducer producer,
            TrafficCsvMapper mapper,
            TrafficReadingRepository readingRepository,
            DatasetImportStateRepository stateRepository,
            MeterRegistry registry
    ) {
        this.datasetBasePath = Path.of(datasetBasePath);
        this.producer = producer;
        this.mapper = mapper;
        this.readingRepository = readingRepository;
        this.stateRepository = stateRepository;

        this.received = registry.counter("ingestion.traffic.rows.received");
        this.skipped = registry.counter("ingestion.traffic.rows.skipped");
        this.importFailed = registry.counter("ingestion.traffic.import.failed");
    }

    public TrafficReadingEvent ingestSingle(TrafficReadingRequest request) {
        TrafficReadingEvent event = TrafficReadingEvent.of(
                "manual-api",
                request.junctionId(),
                request.timestamp(),
                request.vehicles()
        );

        storeAndPublish(event);

        return event;
    }

    public List<String> availableDatasets() {
        return datasetFiles().stream()
                .map(path -> path.getFileName().toString())
                .toList();
    }

    public ImportResponse importNextAvailableFile(long limit) {
        for (String fileName : availableDatasets()) {
            boolean completed = stateRepository.findByFileName(STATE_PREFIX + fileName)
                    .map(DatasetImportState::isCompleted)
                    .orElse(false);

            if (!completed) {
                return importCsv(fileName, limit);
            }
        }

        log.info("No traffic dataset files left to import");
        return new ImportResponse(NO_FILE_AVAILABLE, 0, 0, limit);
    }

    public ImportResponse importCsv(String fileName, long limit) {
        // The path comes from the directory listing, never from the request.
        Path filePath = datasetFiles().stream()
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Traffic CSV file not found: " + fileName));

        String stateKey = STATE_PREFIX + filePath.getFileName();

        DatasetImportState state = stateRepository.findByFileName(stateKey)
                .orElseGet(() -> stateRepository.save(new DatasetImportState(stateKey)));

        if (state.isCompleted()) {
            return new ImportResponse(fileName, 0, 0, limit);
        }

        long nextRecordNumber = state.getNextRecordNumber();
        long importedRows = 0;
        long skippedRows = 0;
        boolean reachedEndOfFile = true;
        RuntimeException failure = null;

        try (
                BufferedReader reader = Files.newBufferedReader(filePath);
                CSVParser parser = CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)
        ) {
            for (CSVRecord record : parser) {
                long recordNumber = record.getRecordNumber();

                if (recordNumber < nextRecordNumber) {
                    continue;
                }

                if (limit > 0 && importedRows + skippedRows >= limit) {
                    reachedEndOfFile = false;
                    break;
                }

                TrafficReadingEvent event;

                try {
                    event = mapper.map(record);
                } catch (RuntimeException ex) {
                    skipped.increment();
                    skippedRows++;
                    nextRecordNumber = recordNumber + 1;
                    log.warn(
                            "Skipping invalid traffic CSV record recordNumber={} fileName={} reason={}",
                            recordNumber, fileName, ex.toString()
                    );
                    continue;
                }

                try {
                    storeAndPublish(event);
                } catch (RuntimeException ex) {
                    failure = ex;
                    reachedEndOfFile = false;
                    break;
                }

                importedRows++;
                nextRecordNumber = recordNumber + 1;
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read traffic CSV file " + fileName, ex);
        }

        state.setNextRecordNumber(nextRecordNumber);
        state.setImportedRows(state.getImportedRows() + importedRows);
        state.setSkippedRows(state.getSkippedRows() + skippedRows);
        state.setCompleted(reachedEndOfFile);
        state.setUpdatedAt(Instant.now());
        stateRepository.save(state);

        if (failure != null) {
            importFailed.increment();
            throw new IllegalStateException(
                    "Traffic import stopped at record " + nextRecordNumber + " of " + fileName
                            + "; it will continue from there",
                    failure
            );
        }

        log.info(
                "Traffic batch import finished fileName={} importedRows={} skippedRows={} completed={}",
                fileName, importedRows, skippedRows, reachedEndOfFile
        );

        return new ImportResponse(fileName, importedRows, skippedRows, limit);
    }

    private void storeAndPublish(TrafficReadingEvent event) {
        readingRepository.insertIgnoreDuplicate(event);
        producer.publish(event);
        received.increment();
    }

    private List<Path> datasetFiles() {
        if (!Files.isDirectory(datasetBasePath)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(datasetBasePath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".csv"))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot list traffic datasets in " + datasetBasePath, ex);
        }
    }
}
