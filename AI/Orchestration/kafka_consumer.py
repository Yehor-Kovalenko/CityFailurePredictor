import json
import threading
import time
from confluent_kafka import Consumer, KafkaException, KafkaError, Message
from dataclasses import dataclass
from typing import Callable

import logging
from AI.Domain.Datasets.electricity_reading import ElectricityReading
from AI.Orchestration.request_manager import Request

logger = logging.getLogger(__name__)

MALFORMED_MESSAGE_ERRORS = (ValueError, KeyError, TypeError, AttributeError, UnicodeDecodeError)


class MessageProcessingError(RuntimeError):
    pass


@dataclass
class KafkaConsumerConfig:
    bootstrap_servers: str
    group_id: str
    topic: str
    predictions_topic: str
    auto_offset_reset: str = "earliest"
    enable_auto_commit: bool = False
    max_retries: int = 5
    retry_backoff_seconds: float = 1.0


class ElectricityReadingKafkaConsumer:
    """
    At-least-once processing: an offset is committed only after the handler
    succeeded. A message that cannot be parsed is logged and skipped, because
    retrying it can never work. A message whose handling keeps failing stops
    the consumer without a commit, so it is delivered again after a restart.
    """

    def __init__(self, config: KafkaConsumerConfig, consumer=None):
        self.config = config
        self.skipped_messages = 0
        self._stop_requested = threading.Event()

        self.consumer = consumer or Consumer({
            "bootstrap.servers": config.bootstrap_servers,
            "group.id": config.group_id,
            "auto.offset.reset": config.auto_offset_reset,
            "enable.auto.commit": config.enable_auto_commit,
        })

    def stop(self) -> None:
        self._stop_requested.set()

    def start(self, handler: Callable[[Request], None]) -> None:
        logger.info("Starting Kafka consumer topic=%s group_id=%s", self.config.topic, self.config.group_id)

        self.consumer.subscribe([self.config.topic])

        try:
            while not self._stop_requested.is_set():
                message = self.consumer.poll(timeout=1.0)

                if message is None:
                    continue

                if message.error():
                    self._handle_error(message)
                    continue

                try:
                    request = self._map_message_to_request(message)
                except MALFORMED_MESSAGE_ERRORS:
                    self.skipped_messages += 1
                    logger.exception(
                        "Skipping malformed Kafka message topic=%s partition=%s offset=%s",
                        message.topic(),
                        message.partition(),
                        message.offset(),
                    )
                    self.consumer.commit(message=message, asynchronous=False)
                    continue

                self._handle_with_retry(handler, request, message)

                self.consumer.commit(message=message, asynchronous=False)

        finally:
            logger.info("Closing Kafka consumer")
            self.consumer.close()

    def _handle_with_retry(self, handler: Callable[[Request], None], request: Request, message: Message) -> None:
        attempts = max(1, self.config.max_retries)

        for attempt in range(1, attempts + 1):
            try:
                handler(request)
                return
            except Exception as error:
                logger.exception(
                    "Failed to process Kafka message partition=%s offset=%s attempt=%s/%s",
                    message.partition(),
                    message.offset(),
                    attempt,
                    attempts,
                )

                if attempt == attempts:
                    raise MessageProcessingError(
                        f"Giving up on partition={message.partition()} offset={message.offset()} "
                        f"after {attempts} attempts; the offset is not committed"
                    ) from error

                time.sleep(self.config.retry_backoff_seconds * attempt)

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
