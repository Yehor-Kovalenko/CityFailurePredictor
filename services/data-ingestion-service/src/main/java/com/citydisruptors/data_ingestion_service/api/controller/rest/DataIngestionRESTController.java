package com.citydisruptors.data_ingestion_service.api.controller.rest;

import com.citydisruptors.data_ingestion_service.api.dto.ElectricityReadingRequest;
import com.citydisruptors.data_ingestion_service.api.dto.ImportResponse;
import com.citydisruptors.data_ingestion_service.config.kafka.events.ElectricityReadingEvent;
import com.citydisruptors.data_ingestion_service.service.ElectricityIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/data-ingestion")
public class DataIngestionRESTController {

    private final ElectricityIngestionService service;

    public DataIngestionRESTController(ElectricityIngestionService service) {
        this.service = service;
    }

    @PostMapping("/electricity")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ElectricityReadingEvent ingestSingle(@RequestBody @Valid ElectricityReadingRequest request) {
        return service.ingestSingle(request);
    }

    @PostMapping("/electricity/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportResponse importElectricityCsv(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "10000") long limit
    ) {
        return service.importCsv(fileName, limit);
    }

    @GetMapping("/test")
    public String test() {
        return "DATA INGESTION OK";
    }

    @GetMapping("/datasets")
    public List<String> availableDatasets() {
        return service.availableDatasets();
    }
}
