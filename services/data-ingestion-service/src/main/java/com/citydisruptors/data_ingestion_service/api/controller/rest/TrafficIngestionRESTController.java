package com.citydisruptors.data_ingestion_service.api.controller.rest;

import com.citydisruptors.data_ingestion_service.api.dto.ImportResponse;
import com.citydisruptors.data_ingestion_service.api.dto.TrafficReadingRequest;
import com.citydisruptors.data_ingestion_service.config.kafka.events.TrafficReadingEvent;
import com.citydisruptors.data_ingestion_service.service.TrafficIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/data-ingestion/traffic")
public class TrafficIngestionRESTController {

    private final TrafficIngestionService service;

    public TrafficIngestionRESTController(TrafficIngestionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TrafficReadingEvent ingestSingle(@RequestBody @Valid TrafficReadingRequest request) {
        return service.ingestSingle(request);
    }

    @GetMapping("/datasets")
    public List<String> availableDatasets() {
        return service.availableDatasets();
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportResponse importTrafficCsv(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "1000") long limit
    ) {
        return service.importCsv(fileName, limit);
    }

    @PostMapping("/import/next")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ImportResponse importNextTrafficBatch(
            @RequestParam(defaultValue = "200") long limit
    ) {
        return service.importNextAvailableFile(limit);
    }
}
