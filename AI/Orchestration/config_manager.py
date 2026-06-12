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
            config = json.load(f)

        # safe generic logging
        model_type = config.get("model", {}).get("type")
        model_path = config.get("model", {}).get("path")

        features = config.get("features", {})

        logger.info(
            "Config loaded task=%s model=%s path=%s features_keys=%s",
            task,
            model_type,
            model_path,
            list(features.keys()) if isinstance(features, dict) else None
        )

        return config