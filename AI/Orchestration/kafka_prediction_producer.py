import json
from confluent_kafka import Producer

import logging

logger = logging.getLogger(__name__)


class PredictionKafkaProducer:

    def __init__(self, config):
        self.topic = config.predictions_topic
        self.producer = Producer({
            "bootstrap.servers": config.bootstrap_servers
        })

    def publish(self, key: str, event: dict) -> None:
        payload = json.dumps(event, default=str).encode("utf-8")

        self.producer.produce(
            topic=self.topic,
            key=key.encode("utf-8"),
            value=payload,
            callback=self._delivery_report
        )

        self.producer.flush()

    def _delivery_report(self, err, msg):
        if err:
            logger.error("Prediction publish failed: %s", err)
        else:
            logger.info(
                "Prediction published topic=%s partition=%s offset=%s",
                msg.topic(),
                msg.partition(),
                msg.offset()
            )
