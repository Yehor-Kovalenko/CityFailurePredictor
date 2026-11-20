from dataclasses import dataclass

SURGE = "SURGE"
DROP = "DROP"


@dataclass(frozen=True)
class ObservationAssessment:
    anomaly_type: str | None
    score: float

    @property
    def is_anomaly(self) -> bool:
        return self.anomaly_type is not None


class TrafficAnomalyDetector:
    """
    Two signals, both per junction:

    - anomaly: the measured number of vehicles is far from what the model
      expected for that hour. Far above is a SURGE (an unexpected jam), far
      below is a DROP (traffic that stopped arriving, e.g. a closed road).
      "Far" means at least `z_threshold` forecast standard deviations and at
      least `min_relative_deviation` of the expected value, so that quiet
      junctions do not raise alarms over a handful of vehicles.
    - congestion: the forecast for the next hour reaches the volume at which
      the junction is considered congested (`congestion_thresholds`).
    """

    def __init__(self, z_threshold=3.0, min_relative_deviation=0.5, congestion_thresholds=None):
        self.z_threshold = z_threshold
        self.min_relative_deviation = min_relative_deviation
        self.congestion_thresholds = {
            str(junction): float(threshold)
            for junction, threshold in (congestion_thresholds or {}).items()
        }

    def assess_observation(self, observed, expected, std) -> ObservationAssessment:
        score = (observed - expected) / std if std and std > 0 else 0.0

        far_enough = abs(observed - expected) >= self.min_relative_deviation * max(expected, 1.0)

        if far_enough and score >= self.z_threshold:
            return ObservationAssessment(SURGE, score)

        if far_enough and score <= -self.z_threshold:
            return ObservationAssessment(DROP, score)

        return ObservationAssessment(None, score)

    def congestion_threshold(self, junction_id) -> float | None:
        return self.congestion_thresholds.get(str(junction_id))

    def congestion_expected(self, junction_id, predicted_vehicles) -> bool:
        threshold = self.congestion_threshold(junction_id)

        return threshold is not None and predicted_vehicles >= threshold
