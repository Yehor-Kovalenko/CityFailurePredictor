package com.citydisruptors.data_ingestion_service.scheduler;

import com.citydisruptors.data_ingestion_service.service.TrafficIngestionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficDatasetSchedulerTest {

    @Test
    void doesNothingWhenDisabled() {
        TrafficIngestionService service = mock(TrafficIngestionService.class);

        new TrafficDatasetScheduler(service, false, 200).importNextBatch();

        verify(service, never()).importNextAvailableFile(anyLong());
    }

    @Test
    void importsNextBatchWhenEnabledAndSurvivesFailures() {
        TrafficIngestionService service = mock(TrafficIngestionService.class);
        when(service.importNextAvailableFile(200)).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> new TrafficDatasetScheduler(service, true, 200).importNextBatch())
                .doesNotThrowAnyException();

        verify(service).importNextAvailableFile(200);
    }
}
