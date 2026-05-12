from dataclasses import dataclass
import json


@dataclass
class ModelConfig:
    model_type: str
    model_version: str


class ConfigManager:

    def __init__(self, config_path: str):
        self.task_configs = self._load_configs(config_path)

    def _load_configs(self, config_path: str):

        with open(config_path, "r") as file:
            raw_config = json.load(file)

        configs = {}

        for task, config in raw_config.items():

            configs[task] = ModelConfig(
                model_type=config["model_type"],
                model_version=config["model_version"]
            )

        return configs

    def get_config(self, task: str) -> ModelConfig:

        if task not in self.task_configs:
            raise ValueError(f"No configuration for task: {task}")

        return self.task_configs[task]