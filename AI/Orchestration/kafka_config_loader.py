import json
import os

from AI.Orchestration.kafka_consumer import KafkaConsumerConfig

STREAMS = {
    "electricity": ("electricity_raw", "predictions_generated", "group_id"),
    "traffic": ("traffic_raw", "traffic_predictions_generated", "traffic_group_id"),
}


class KafkaConfigLoader:

    @staticmethod
    def load(config_path: str, stream: str = "electricity") -> KafkaConsumerConfig:
        with open(config_path, "r") as file:
            raw_config = json.load(file)

        bootstrap_servers = os.getenv(
            "KAFKA_BOOTSTRAP_SERVERS",
            raw_config["bootstrap_servers"]
        )

        input_topic, output_topic, group_key = STREAMS[stream]

        return KafkaConsumerConfig(
            bootstrap_servers=bootstrap_servers,
            group_id=raw_config[group_key],
            topic=raw_config["topics"][input_topic],
            predictions_topic=raw_config["topics"][output_topic],
            auto_offset_reset=raw_config.get("auto_offset_reset", "earliest"),
            enable_auto_commit=raw_config.get("enable_auto_commit", False),
        )
