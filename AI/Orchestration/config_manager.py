import json
import logging

logger = logging.getLogger(__name__)


class ConfigManager:

    def __init__(self):
        self.configs_dir = "../Domain/Resources/Configs"

    def resolve(self, task: str):
        path = f"{self.configs_dir}/{task}/inference_config.json"

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

        else:
            raise ValueError(f"Unknown task: {task}")

        logger.info("Config loaded task=%s keys=%s", task, list(config.keys()))

        return config