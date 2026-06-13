import json
import logging
from pathlib import Path

logger = logging.getLogger(__name__)


class ConfigManager:

    def __init__(self):
        # get project root relative to this file (stable everywhere)
        self.base_dir = Path(__file__).resolve().parents[1]
        self.configs_dir = self.base_dir / "Domain" / "Resources" / "Configs"

    def resolve(self, task: str):
        path = self.configs_dir / task / "inference_config.json"

        logger.info("Loading config task=%s path=%s", task, path)

        with open(path, "r") as f:
            raw = json.load(f)

        if task == "electricity":
            config = {
                "model_type": raw.get("model", {}).get("type"),
                "model_path": raw.get("model", {}).get("path"),
                "features": raw.get("features", {}),
            }

        elif task == "vrp":
            config = {
                "vehicle_capacity": raw.get("vehicle_capacity"),
                "n_ants": raw.get("n_ants"),
                "iterations": raw.get("iterations"),
                "evaporation": raw.get("evaporation"),
                "alpha": raw.get("alpha"),
                "beta": raw.get("beta"),
                "coef_urgency": raw.get("coef_urgency"),
                "tabu_max_iterations": raw.get("tabu_max_iterations"),
                "tabu_tenure": raw.get("tabu_tenure"),
            }

        elif task == "traffic":
            config = {
                "model_type": raw.get("model", {}).get("type"),
                "model_path": raw.get("model", {}).get("path"),

                "features": {
                    "window_size": raw.get("features", {}).get("window_size"),
                    "use_hour": raw.get("features", {}).get("use_hour"),
                    "use_day_of_week": raw.get("features", {}).get("use_day_of_week"),
                    "use_lag_24": raw.get("features", {}).get("use_lag_24"),
                    "use_lag_168": raw.get("features", {}).get("use_lag_168"),
                    "use_rolling_mean_24": raw.get("features", {}).get("use_rolling_mean_24"),
                },

                "forecast": {
                    "horizon": raw.get("forecast", {}).get("horizon", 24)
                }
            }

        else:
            raise ValueError(f"Unknown task: {task}")

        logger.info("Config loaded task=%s keys=%s", task, list(config.keys()))

        return config