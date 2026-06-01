package com.citydisruptors.incidentservice.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class IncidentTest {

    @Test
    void shouldHaveDefaultStatusOfOpen() {
        Incident incident = new Incident();
        assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void shouldSetTimestampOnPrePersist() {
        Coordinates coords = new Coordinates("EPSG:4326", "51.759", "19.457");
        Incident incident = new Incident("Test test title", "A test summary", coords, IncidentType.FIRE);


        incident.onCreate();

        assertThat(incident.getTimestamp()).isNotNull();
        assertThat(incident.getLastUpdated()).isNotNull();
        assertThat(incident.getTimestamp()).isEqualTo(incident.getLastUpdated());
    }

    @Test
    void shouldUpdateLastUpdatedOnPreUpdate() throws InterruptedException {
        Coordinates coords = new Coordinates("EPSG:4326", "51.759", "19.457");
        Incident incident = new Incident("Test test title", "A test summary", coords, IncidentType.FIRE);
        incident.onCreate();

        Instant createdAt = incident.getTimestamp();
        Thread.sleep(10);
        incident.onUpdate();

        assertThat(incident.getLastUpdated()).isAfter(createdAt);
        assertThat(incident.getTimestamp()).isEqualTo(createdAt);
    }

    @Test
    void shouldStoreAllFieldsCorrectly() {
        Coordinates coords = new Coordinates("EPSG:4326", "51.759", "19.457");

        Incident incident = new Incident("Test Fire", "A test summary", coords, IncidentType.FIRE);

        assertThat(incident.getIncidentTitle()).isEqualTo("Test Fire");
        assertThat(incident.getIncidentSummary()).isEqualTo("A test summary");
        assertThat(incident.getCoordinates().crs()).isEqualTo("EPSG:4326");
        assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FIRE);
        assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.OPEN);
    }
}