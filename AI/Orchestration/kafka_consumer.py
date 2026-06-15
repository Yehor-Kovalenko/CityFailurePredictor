import json
import time
from confluent_kafka import Consumer, KafkaException, KafkaError, Message
from dataclasses import dataclass
from typing import Callable

import logging
from AI.Domain.Datasets.electricity_reading import ElectricityReading
from AI.Orchestration.request_manager import Request

logger = logging.getLogger(__name__)


@dataclass
class KafkaConsumerConfig:
    bootstrap_servers: str
    group_id: str
    topic: str
    predictions_topic: str
    auto_offset_reset: str = "earliest"
    enable_auto_commit: bool = False


class ElectricityReadingKafkaConsumer:
    def __init__(self, config: KafkaConsumerConfig):
        self.config = config
        self.consumer = Consumer({
            "bootstrap.servers": config.bootstrap_servers,
            "group.id": config.group_id,
            "auto.offset.reset": config.auto_offset_reset,
            "enable.auto.commit": config.enable_auto_commit,
        })

    def start(self, handler: Callable[[Request], None]) -> None:
        logger.info("Starting Kafka consumer topic=%s group_id=%s", self.config.topic, self.config.group_id)

        self.consumer.subscribe([self.config.topic])

        try:
            while True:
                message = self.consumer.poll(timeout=1.0)

                if message is None:
                    continue

                if message.error():
                    self._handle_error(message)
                    continue

                try:
                    request = self._map_message_to_request(message)
                    handler(request)

                    self.consumer.commit(message=message, asynchronous=False)

                except Exception:
                    logger.exception("Failed to process Kafka message. Message will not be committed.")

        finally:
            logger.info("Closing Kafka consumer")
            self.consumer.close()

    def _map_message_to_request(self, message: Message) -> Request:
        raw_value = message.value().decode("utf-8")
        event = json.loads(raw_value)

        reading = ElectricityReading.from_kafka_event(event)

        return Request(
            task="electricity",
            data=reading,
            timestamp=reading.reading_timestamp
        )

    def _handle_error(self, message: Message) -> None:
        error = message.error()

        if error.code() == KafkaError._PARTITION_EOF:
            logger.debug("Reached end of partition")
            return

        if error.code() == KafkaError.UNKNOWN_TOPIC_OR_PART:
            logger.warning(
                "Topic %s is not available yet. Waiting...",
                self.config.topic
            )
            time.sleep(5)
            return

        raise KafkaException(error)
