package com.citydisruptors.data_ingestion_service.scheduler;

import com.citydisruptors.data_ingestion_service.service.ElectricityIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ElectricityDatasetScheduler {

    private static final Logger log = LoggerFactory.getLogger(ElectricityDatasetScheduler.class);

    private final ElectricityIngestionService ingestionService;
    private final boolean enabled;
    private final long batchSize;

    public ElectricityDatasetScheduler(
            ElectricityIngestionService ingestionService,
            @Value("${app.ingestion.scheduler-enabled:false}") boolean enabled,
            @Value("${app.ingestion.batch-size:1000}") long batchSize
    ) {
        this.ingestionService = ingestionService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.ingestion.fixed-delay-ms:10000}")
    public void importNextBatch() {
        if (!enabled) {
            return;
        }

        try {
            ingestionService.importNextAvailableFile(batchSize);
        } catch (Exception ex) {
            log.error("Scheduled electricity import failed", ex);
        }
    }
}