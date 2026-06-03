import json
import os

from Orchestration.kafka_consumer import KafkaConsumerConfig


class KafkaConfigLoader:

    @staticmethod
    def load(config_path: str) -> KafkaConsumerConfig:
        with open(config_path, "r") as file:
            raw_config = json.load(file)

        bootstrap_servers = os.getenv(
            "KAFKA_BOOTSTRAP_SERVERS",
            raw_config["bootstrap_servers"]
        )

        return KafkaConsumerConfig(
            bootstrap_servers=bootstrap_servers,
            group_id=raw_config["group_id"],
            topic=raw_config["topics"]["electricity_raw"],
            auto_offset_reset=raw_config.get("auto_offset_reset", "earliest"),
            enable_auto_commit=raw_config.get("enable_auto_commit", False)
        )
