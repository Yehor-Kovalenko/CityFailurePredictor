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

    @staticmethod
    def from_traffic_result(reading, inference_result: dict, observation: dict | None, congestion: dict) -> dict:
        """
        observation: how the reading compares with the forecast made for its
        hour (None when there was no such forecast); congestion: threshold and
        verdict for the forecast hour.
        """

        uncertainty = inference_result.get("uncertainty")

        return {
            "predictionBatchId": reading.event_id,
            "source": "ai-service",
            "junctionId": reading.junction_id,
            "generatedAt": to_java_instant(datetime.now(timezone.utc)),
            "modelType": inference_result["model_type"],
            "modelVersion": inference_result.get("model_version", "unknown"),
            "observation": observation,
            "predictions": [
                {
                    "targetTimestamp": to_java_instant(inference_result["target_timestamp"]),
                    "predictedVehicles": max(0.0, float(inference_result["next_prediction"])),
                    "confidence": 0.95 if uncertainty else None,
                    "lowerBoundVehicles": max(0.0, float(uncertainty["lower_95"])) if uncertainty else None,
                    "upperBoundVehicles": max(0.0, float(uncertainty["upper_95"])) if uncertainty else None,
                    "congestionThreshold": congestion["threshold"],
                    "congestionExpected": congestion["expected"],
                }
            ]
        }
