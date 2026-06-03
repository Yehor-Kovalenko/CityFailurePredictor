package com.citydisruptors.data_ingestion_service.scheduler;

import com.citydisruptors.data_ingestion_service.service.ElectricityIngestionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ElectricityDatasetSchedulerTest {

    @Test
    void shouldNotRunImportWhenSchedulerDisabled() {
        ElectricityIngestionService service = mock(ElectricityIngestionService.class);
        ElectricityDatasetScheduler scheduler = new ElectricityDatasetScheduler(service, false, 1000);

        scheduler.importNextBatch();

        verifyNoInteractions(service);
    }

    @Test
    void shouldRunImportWhenSchedulerEnabled() {
        ElectricityIngestionService service = mock(ElectricityIngestionService.class);
        ElectricityDatasetScheduler scheduler = new ElectricityDatasetScheduler(service, true, 1000);

        scheduler.importNextBatch();

        verify(service).importNextAvailableFile(1000);
    }

    @Test
    void shouldNotThrowWhenImportFails() {
        ElectricityIngestionService service = mock(ElectricityIngestionService.class);
        doThrow(new RuntimeException("Import failed"))
                .when(service)
                .importNextAvailableFile(1000);

        ElectricityDatasetScheduler scheduler = new ElectricityDatasetScheduler(service, true, 1000);

        scheduler.importNextBatch();

        verify(service).importNextAvailableFile(1000);
    }
}