import json
from confluent_kafka import Producer

import logging

logger = logging.getLogger(__name__)

MESSAGE_TIMEOUT_MS = 10_000
FLUSH_TIMEOUT_SECONDS = 15.0


class PredictionPublishError(RuntimeError):
    pass


class PredictionKafkaProducer:

    def __init__(self, config, producer=None):
        self.topic = config.predictions_topic
        self.producer = producer or Producer({
            "bootstrap.servers": config.bootstrap_servers,
            "enable.idempotence": True,
            "message.timeout.ms": MESSAGE_TIMEOUT_MS,
        })

    def publish(self, key: str, event: dict) -> None:
        """Blocks until the broker confirms the message; raises if it does not."""

        payload = json.dumps(event, default=str, allow_nan=False).encode("utf-8")
        errors = []

        def on_delivery(err, msg):
            if err:
                errors.append(err)
                return

            logger.info(
                "Prediction published topic=%s partition=%s offset=%s",
                msg.topic(),
                msg.partition(),
                msg.offset()
            )

        self.producer.produce(
            topic=self.topic,
            key=key.encode("utf-8"),
            value=payload,
            callback=on_delivery
        )

        undelivered = self.producer.flush(FLUSH_TIMEOUT_SECONDS)

        if errors:
            raise PredictionPublishError(f"Prediction publish failed: {errors[0]}")

        if undelivered:
            raise PredictionPublishError(
                f"Prediction was not confirmed within {FLUSH_TIMEOUT_SECONDS}s"
            )
