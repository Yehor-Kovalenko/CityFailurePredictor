package com.citydisruptors.incidentservice.config;

import com.citydisruptors.incidentservice.api.dto.CreateIncidentRequest;
import com.citydisruptors.incidentservice.entity.Coordinates;
import com.citydisruptors.incidentservice.entity.IncidentType;
import com.citydisruptors.incidentservice.service.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentDemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IncidentDemoDataInitializer.class);

    private final IncidentService incidentService;
    private final boolean enabled;

    public IncidentDemoDataInitializer(
            IncidentService incidentService,
            @Value("${app.demo-data.enabled:true}") boolean enabled
    ) {
        this.incidentService = incidentService;
        this.enabled = enabled;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        log.info("Demo incident initializer started. enabled={}", enabled);

        if (!enabled) {
            log.info("Demo incident initializer skipped because app.demo-data.enabled=false");
            return;
        }

        var existingIncidents = incidentService.getAll(null, null);
        log.info("Existing incidents count={}", existingIncidents.size());

        if (!existingIncidents.isEmpty()) {
            log.info("Demo incidents skipped because database already contains incidents");
            return;
        }

        List<CreateIncidentRequest> demoIncidents = List.of(
                incident("Awaria zasilania w Śródmieściu", "Przerwa w dostawie energii w okolicy Piotrkowskiej.", 51.7592, 19.4559, IncidentType.ELECTRICITY),
                incident("Zalanie ulicy Zachodniej", "Uszkodzona instalacja wodociągowa powoduje zalanie jezdni.", 51.7731, 19.4512, IncidentType.WATER),
                incident("Kolizja przy Łodzi Fabrycznej", "Zdarzenie drogowe w pobliżu dworca Łódź Fabryczna.", 51.7694, 19.4671, IncidentType.ACCIDENT),
                incident("Pożar instalacji w Starym Widzewie", "Zgłoszenie zadymienia przy budynku technicznym.", 51.7607, 19.5002, IncidentType.FIRE),
                incident("Uszkodzona nawierzchnia na Polesiu", "Zapadnięcie fragmentu drogi po opadach.", 51.7558, 19.4145, IncidentType.ROADS),
                incident("Podtopienie przy Parku Źródliska", "Nagromadzenie wody po intensywnych opadach.", 51.7596, 19.4815, IncidentType.FLOOD),
                incident("Awaria latarni na Retkini", "Brak oświetlenia ulicznego na kilku odcinkach.", 51.7475, 19.3918, IncidentType.ELECTRICITY),
                incident("Pęknięta rura na Bałutach", "Spadek ciśnienia wody i wyciek na chodniku.", 51.7890, 19.4564, IncidentType.WATER),
                incident("Wypadek na trasie W-Z", "Utrudnienia w ruchu w kierunku centrum.", 51.7550, 19.4702, IncidentType.ACCIDENT),
                incident("Pożar śmietnika przy Kilińskiego", "Niewielki pożar, ryzyko rozprzestrzenienia.", 51.7652, 19.4718, IncidentType.FIRE),
                incident("Dziura w jezdni na Widzewie", "Niebezpieczne uszkodzenie nawierzchni.", 51.7468, 19.5060, IncidentType.ROADS),
                incident("Zalany przejazd pod wiaduktem", "Woda blokuje przejazd po intensywnym deszczu.", 51.7422, 19.4478, IncidentType.FLOOD),
                incident("Awaria transformatora przy kampusie PŁ", "Niestabilne zasilanie w okolicy kampusu.", 51.7535, 19.4490, IncidentType.ELECTRICITY),
                incident("Wyciek wody przy Manufakturze", "Podejrzenie awarii sieci wodociągowej.", 51.7790, 19.4475, IncidentType.WATER),
                incident("Kolizja przy Rondzie Solidarności", "Zderzenie dwóch pojazdów, utrudnienia w ruchu.", 51.7754, 19.4805, IncidentType.ACCIDENT)
        );

        demoIncidents.forEach(request -> {
            var created = incidentService.create(request);
            if (created == null) {
                log.warn("Demo incident was not created: {}", request.incidentTitle());
            } else {
                log.info("Demo incident created: {}", created.incidentTitle());
            }
        });

        log.info("Demo incident initializer finished");
    }

    private CreateIncidentRequest incident(
            String title,
            String summary,
            double latitude,
            double longitude,
            IncidentType type
    ) {
        return new CreateIncidentRequest(
                title,
                summary,
                new Coordinates("EPSG:4326", String.valueOf(latitude), String.valueOf(longitude)),
                type
        );
    }
}