package com.citydisruptors.data_ingestion_service.service;

import com.citydisruptors.data_ingestion_service.api.dto.ElectricityReadingRequest;
import com.citydisruptors.data_ingestion_service.api.dto.ImportResponse;
import com.citydisruptors.data_ingestion_service.config.kafka.events.ElectricityReadingEvent;
import com.citydisruptors.data_ingestion_service.config.kafka.producers.ElectricityReadingProducer;
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

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    public ElectricityIngestionService(
            @Value("${app.dataset.base-path}") String datasetBasePath,
            ElectricityReadingProducer producer,
            LowCarbonLondonCsvMapper mapper,
            MeterRegistry registry
    ) {
        this.datasetBasePath = Path.of(datasetBasePath);
        this.producer = producer;
        this.mapper = mapper;
        this.received = registry.counter("ingestion.electricity.rows.received");
        this.skipped = registry.counter("ingestion.electricity.rows.skipped");
        this.importTimer = registry.timer("ingestion.electricity.import.duration");
        this.importStarted = registry.counter("ingestion.electricity.import.started");
        this.importFinished = registry.counter("ingestion.electricity.import.finished");
        this.importFailed = registry.counter("ingestion.electricity.import.failed");
    }

    public ElectricityReadingEvent ingestSingle(ElectricityReadingRequest request) {
        ElectricityReadingEvent event = ElectricityReadingEvent.of(
                "manual-api",
                request.householdId(),
                request.tariffType(),
                request.timestamp(),
                request.kwh()
        );

        log.info(
                "Single electricity reading accepted householdId={} tariffType={} timestamp={} kwh={}",
                request.householdId(),
                request.tariffType(),
                request.timestamp(),
                request.kwh()
        );

        received.increment();
        producer.publish(event);
        return event;
    }

    public ImportResponse importCsv(String fileName, long limit) {
        importStarted.increment();

        try {
            ImportResponse response = importTimer.record(() -> doImportCsv(fileName, limit));
            importFinished.increment();
            return response;
        } catch (Exception ex) {
            importFailed.increment();
            throw ex;
        }
    }

    private ImportResponse doImportCsv(String fileName, long limit) {
        Path filePath = datasetBasePath.resolve(fileName).normalize();

        log.info("Electricity dataset import started fileName={} limit={} path={}", fileName, limit, filePath);

        if (!filePath.startsWith(datasetBasePath)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("CSV file not found: " + fileName);
        }

        long importedRows = 0;
        long skippedRows = 0;

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
                if (limit > 0 && importedRows >= limit) {
                    break;
                }

                try {
                    ElectricityReadingEvent event = mapper.map(record);
                    producer.publish(event);
                    received.increment();
                    importedRows++;

                    if (importedRows % 1000 == 0) {
                        producer.flush();
                    }

                } catch (Exception ex) {
                    log.warn("Skipping invalid electricity CSV record recordNumber={} fileName={}", record.getRecordNumber(), fileName);
                    skipped.increment();
                    skippedRows++;
                }
            }

            producer.flush();

            log.info(
                    "Electricity dataset import finished fileName={} importedRows={} skippedRows={} limit={}",
                    fileName,
                    importedRows,
                    skippedRows,
                    limit
            );

            return new ImportResponse(fileName, importedRows, skippedRows, limit);

        } catch (Exception ex) {
            log.error("Electricity dataset import failed fileName={} limit={}", fileName, limit, ex);
            throw new IllegalStateException("CSV import failed: " + fileName, ex);
        }
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
}
