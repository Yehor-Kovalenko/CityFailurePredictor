package com.citydisruptors.incidentservice.repository;

import com.citydisruptors.incidentservice.entity.Coordinates;
import com.citydisruptors.incidentservice.entity.Incident;
import com.citydisruptors.incidentservice.entity.IncidentStatus;
import com.citydisruptors.incidentservice.entity.IncidentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IncidentRepositoryTest {

    @Autowired
    private IncidentRepository repository;

    @Test
    void shouldFindAllIncidents() {
        List<Incident> all = repository.findAll();
        assertThat(all).hasSize(5);
    }

    @Test
    void shouldFindByStatus() {
        List<Incident> open = repository.findByIncidentStatus(IncidentStatus.OPEN);
        assertThat(open).allMatch(i -> i.getIncidentStatus() == IncidentStatus.OPEN);
        assertThat(open.size()).isEqualTo(1);
    }

    @Test
    void shouldFindByIncidentType() {
        List<Incident> fires = repository.findByIncidentType(IncidentType.FIRE);
        assertThat(fires).allMatch(i -> i.getIncidentType() == IncidentType.FIRE);
    }

    @Test
    void shouldPersistNewIncident() {
        Incident incident = new Incident(
                "New Test Incident", "Summary",
                new Coordinates("EPSG:4326", "51.0", "19.0"), IncidentType.FIRE);

        Incident saved = repository.save(incident);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIncidentStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void shouldDeleteIncident() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        repository.deleteById(id);
        assertThat(repository.findById(id)).isEmpty();
    }
}
