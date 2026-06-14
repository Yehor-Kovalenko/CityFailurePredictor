import json
import logging
from pathlib import Path

logger = logging.getLogger(__name__)


class ConfigManager:

    def __init__(self):
        self.base_dir = Path(__file__).resolve().parents[1]
        self.configs_dir = self.base_dir / "Domain" / "Resources" / "Configs"
        self.weights_dir = self.base_dir / "Domain" / "Resources" / "ModelWeights"

    def resolve(self, task: str):

        path = self.configs_dir / task / "inference_config.json"

        logger.info("Loading config task=%s path=%s", task, path)

        with open(path, "r") as f:
            raw = json.load(f)



        if task == "electricity":

            model = raw["model"]

            model_path = (self.weights_dir / model["path"]).resolve()

            config = {
                "model": {
                    "type": model["type"],
                    "path": str(model_path),
                },
                "features": {
                    "window_size": raw["features"]["window_size"],
                    "lookback_hours": raw["features"]["lookback_hours"],
                    "horizon": raw["features"]["horizon"],
                },
            }


        elif task == "traffic":

            model = raw["model"]

            model_path = (self.weights_dir / model["path"]).resolve()

            config = {

                "model": {
                    "type": raw["model"]["type"],
                    "path": str(model_path),
                },

                "features": {
                    "window_size": raw["features"]["window_size"],
                    "use_hour": raw["features"]["use_hour"],
                    "use_day_of_week": raw["features"]["use_day_of_week"],
                },

                "forecast": {
                    "horizon": raw["forecast"]["horizon"],
                },

            }

        elif task == "vrp":

            config = {
                "task": "vrp",

                "optimizer": {
                    "vehicle_capacity": raw["vehicle_capacity"],
                    "n_ants": raw["n_ants"],
                    "iterations": raw["iterations"],
                    "evaporation": raw["evaporation"],
                    "alpha": raw["alpha"],
                    "beta": raw["beta"],
                    "coef_urgency": raw["coef_urgency"],
                    "tabu_max_iterations": raw["tabu_max_iterations"],
                    "tabu_tenure": raw["tabu_tenure"],
                },

                # optional: keeps inference layer clean & future-proof
                "runtime": {
                    "mode": "optimization",
                    "requires_model": False
                }
            }

        else:
            raise ValueError(f"Unknown task: {task}")

        logger.info(
            "Config loaded task=%s keys=%s",
            task,
            list(config.keys())
        )

        return config
