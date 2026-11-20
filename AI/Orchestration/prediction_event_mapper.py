from datetime import datetime, timezone, timedelta


def to_java_instant(dt):
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


class PredictionEventMapper:

    @staticmethod
    def from_energy_result(reading, inference_result: dict) -> dict:
        # Consumption cannot be negative and the decision service rejects such events.
        predicted_kwh = max(0.0, float(inference_result["next_prediction"]))

        uncertainty = inference_result.get("uncertainty")

        if uncertainty:
            confidence = 0.95
            lower_bound = max(0.0, float(uncertainty["lower_95"]))
            upper_bound = max(0.0, float(uncertainty["upper_95"]))
        else:
            confidence = None
            lower_bound = None
            upper_bound = None

        return {
            "predictionBatchId": reading.event_id,
            "source": "ai-service",
            "householdId": reading.household_id,
            "generatedAt": to_java_instant(datetime.now(timezone.utc)),
            "modelType": inference_result["model_type"],
            "modelVersion": inference_result.get("model_version", "unknown"),
            "predictions": [
                {
                    "targetTimestamp": to_java_instant(
                        reading.reading_timestamp + timedelta(minutes=30)
                    ),
                    "predictedKwh": predicted_kwh,
                    "confidence": confidence,
                    "lowerBoundKwh": lower_bound,
                    "upperBoundKwh": upper_bound
                }
            ]
        }
