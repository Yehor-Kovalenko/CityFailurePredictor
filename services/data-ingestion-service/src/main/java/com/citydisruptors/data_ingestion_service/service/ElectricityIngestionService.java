package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.api.dto.ElectricityReadingRequest;
import com.citydisruptors.data_ingestion_service.api.dto.ImportResponse;
import com.citydisruptors.data_ingestion_service.config.kafka.events.ElectricityReadingEvent;
import com.citydisruptors.data_ingestion_service.config.kafka.producers.ElectricityReadingProducer;
import com.citydisruptors.data_ingestion_service.entity.DatasetImportState;
import com.citydisruptors.data_ingestion_service.repository.DatasetImportStateRepository;
import com.citydisruptors.data_ingestion_service.repository.ElectricityReadingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ElectricityIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ElectricityIngestionService.class);

    private final Path datasetBasePath;
    private final ElectricityReadingProducer producer;
    private final LowCarbonLondonCsvMapper mapper;
    private final Counter received;
    private final Counter skipped;
    private final Timer importTimer;
    private final Counter importStarted;
    private final Counter importFinished;
    private final Counter importFailed;
    private final ElectricityReadingRepository readingRepository;
    private final DatasetImportStateRepository stateRepository;
    private final ImportStatusCacheService statusCache;

    public ElectricityIngestionService(
            @Value("${app.dataset.base-path}") String datasetBasePath,
            ElectricityReadingProducer producer,
            LowCarbonLondonCsvMapper mapper,
            MeterRegistry registry,
            ElectricityReadingRepository readingRepository,
            DatasetImportStateRepository stateRepository,
            ImportStatusCacheService statusCache
    ) {
        this.datasetBasePath = Path.of(datasetBasePath);
        this.producer = producer;
        this.mapper = mapper;
        this.readingRepository = readingRepository;
        this.stateRepository = stateRepository;
        this.statusCache = statusCache;

        this.received = registry.counter("ingestion.electricity.rows.received");
        this.skipped = registry.counter("ingestion.electricity.rows.skipped");
        this.importTimer = registry.timer("ingestion.electricity.import.duration");
        this.importStarted = registry.counter("ingestion.electricity.import.started");
        this.importFinished = registry.counter("ingestion.electricity.import.finished");
        this.importFailed = registry.counter("ingestion.electricity.import.failed");
    }

    @Transactional
    public ElectricityReadingEvent ingestSingle(ElectricityReadingRequest request) {
        ElectricityReadingEvent event = ElectricityReadingEvent.of(
                "manual-api",
                request.householdId(),
                request.tariffType(),
                request.timestamp(),
                request.kwh()
        );

        int inserted = readingRepository.insertIgnoreDuplicate(
                UUID.fromString(event.eventId()),
                event.source(),
                event.householdId(),
                event.tariffType(),
                event.readingTimestamp(),
                event.kwh(),
                event.ingestedAt()
        );

        if (inserted > 0) {
            received.increment();
            producer.publish(event);
        }

        return event;
    }

    @Transactional
    public ImportResponse importCsv(String fileName, long limit) {
        importStarted.increment();

        try {
            ImportResponse response = importTimer.record(() -> doImportCsvBatch(fileName, limit));
            importFinished.increment();
            return response;
        } catch (Exception ex) {
            importFailed.increment();
            throw ex;
        }
    }

    private ImportResponse doImportCsvBatch(String fileName, long limit) {
        Path filePath = datasetBasePath.resolve(fileName).normalize();

        if (!filePath.startsWith(datasetBasePath)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("CSV file not found: " + fileName);
        }

        DatasetImportState state = stateRepository.findByFileName(fileName)
                .orElseGet(() -> stateRepository.save(new DatasetImportState(fileName)));

        if (state.isCompleted()) {
            return new ImportResponse(fileName, 0, 0, limit);
        }

        long nextRecordNumber = state.getNextRecordNumber();
        long processedRows = 0;
        long importedRows = 0;
        long skippedRows = 0;
        long lastProcessedRecord = nextRecordNumber - 1;

        log.info(
                "Electricity batch import started fileName={} limit={} nextRecordNumber={}",
                fileName, limit, nextRecordNumber
        );

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
            boolean reachedAnyRecord = false;

            for (CSVRecord record : parser) {
                long recordNumber = record.getRecordNumber();

                if (recordNumber < nextRecordNumber) {
                    continue;
                }

                reachedAnyRecord = true;

                if (limit > 0 && processedRows >= limit) {
                    break;
                }

                processedRows++;
                lastProcessedRecord = recordNumber;

                try {
                    ElectricityReadingEvent event = mapper.map(record);

                    int inserted = readingRepository.insertIgnoreDuplicate(
                            UUID.fromString(event.eventId()),
                            event.source(),
                            event.householdId(),
                            event.tariffType(),
                            event.readingTimestamp(),
                            event.kwh(),
                            event.ingestedAt()
                    );

                    if (inserted > 0) {
                        producer.publish(event);
                        received.increment();
                        importedRows++;
                    }

                } catch (Exception ex) {
                    skipped.increment();
                    skippedRows++;
                    log.warn("Skipping invalid electricity CSV record recordNumber={} fileName={}", recordNumber, fileName);
                }
            }

            producer.flush();

            boolean completed = !reachedAnyRecord || (limit > 0 && processedRows < limit);

            updateState(
                    state,
                    lastProcessedRecord + 1,
                    importedRows,
                    skippedRows,
                    completed
            );

            log.info(
                    "Electricity batch import finished fileName={} importedRows={} skippedRows={} completed={}",
                    fileName, importedRows, skippedRows, completed
            );

            return new ImportResponse(fileName, importedRows, skippedRows, limit);

        } catch (Exception ex) {
            log.error("Electricity batch import failed fileName={} limit={}", fileName, limit, ex);
            throw new IllegalStateException("CSV import failed: " + fileName, ex);
        }
    }

    private void updateState(
            DatasetImportState state,
            long nextRecordNumber,
            long importedRows,
            long skippedRows,
            boolean completed
    ) {
        state.setNextRecordNumber(nextRecordNumber);
        state.setImportedRows(state.getImportedRows() + importedRows);
        state.setSkippedRows(state.getSkippedRows() + skippedRows);
        state.setCompleted(completed);
        state.setUpdatedAt(Instant.now());

        stateRepository.save(state);

        statusCache.updateStatus(
                state.getFileName(),
                state.getImportedRows(),
                state.getSkippedRows(),
                state.isCompleted()
        );
    }

    public List<String> availableDatasets() {

        try (Stream<Path> stream = Files.list(datasetBasePath)) {

            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".csv"))
                    .sorted()
                    .toList();

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public ImportResponse importNextAvailableFile(long limit) {
        List<String> files = availableDatasets();

        for (String fileName : files) {
            DatasetImportState state = stateRepository.findByFileName(fileName)
                    .orElse(null);

            if (state == null || !state.isCompleted()) {
                return importCsv(fileName, limit);
            }
        }

        log.info("No available dataset files left to import");
        return new ImportResponse("NO_FILE_AVAILABLE", 0, 0, limit);
    }
}
