package com.citydisruptors.decision_service.repository;

import com.citydisruptors.decision_service.entity.Decision;
import com.citydisruptors.decision_service.entity.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    Optional<Decision> findByPredictionId(String predictionId);

    List<Decision> findByRiskLevel(RiskLevel riskLevel);

    List<Decision> findByCreatedAtBetween(Instant from, Instant to);
}
