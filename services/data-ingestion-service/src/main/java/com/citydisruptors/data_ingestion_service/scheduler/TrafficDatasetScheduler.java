package com.citydisruptors.data_ingestion_service.scheduler;

import com.citydisruptors.data_ingestion_service.service.TrafficIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrafficDatasetScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrafficDatasetScheduler.class);

    private final TrafficIngestionService ingestionService;
    private final boolean enabled;
    private final long batchSize;

    public TrafficDatasetScheduler(
            TrafficIngestionService ingestionService,
            @Value("${app.ingestion.traffic.scheduler-enabled:false}") boolean enabled,
            @Value("${app.ingestion.traffic.batch-size:200}") long batchSize
    ) {
        this.ingestionService = ingestionService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.ingestion.traffic.fixed-delay-ms:10000}")
    public void importNextBatch() {
        if (!enabled) {
            return;
        }

        try {
            ingestionService.importNextAvailableFile(batchSize);
        } catch (Exception ex) {
            log.error("Scheduled traffic import failed", ex);
        }
    }
}
