import json
from pathlib import Path

import pytest


@pytest.fixture()
def main_module(monkeypatch):
    monkeypatch.chdir(Path(__file__).resolve().parents[2])

    from AI import main

    main.consumer_failed.clear()
    yield main
    main.consumer_failed.clear()


def test_health_is_up_while_consumer_runs(main_module):
    assert main_module.health() == {"status": "UP", "service": "ai-service"}


def test_health_is_down_after_consumer_stops(main_module, monkeypatch):
    class BrokenConsumer:
        def __init__(self, config):
            pass

        def start(self, handler):
            raise RuntimeError("all brokers down")

    monkeypatch.setattr(main_module, "ElectricityReadingKafkaConsumer", BrokenConsumer)
    called = []

    main_module.start_consumer(on_failure=lambda: called.append(True))

    response = main_module.health()

    assert called == [True]
    assert response.status_code == 503
    assert json.loads(response.body)["status"] == "DOWN"


def test_shutdown_stops_running_consumers(main_module, monkeypatch):
    import asyncio
    import threading

    stopped = threading.Event()

    class BlockingConsumer:
        def __init__(self, config):
            pass

        def start(self, handler):
            stopped.wait(timeout=5)

        def stop(self):
            stopped.set()

    monkeypatch.setattr(main_module, "TrafficReadingKafkaConsumer", BlockingConsumer)
    monkeypatch.setattr(main_module, "running_consumers", [])

    thread = threading.Thread(target=main_module.start_traffic_consumer, daemon=True)
    monkeypatch.setattr(main_module, "consumer_threads", [thread])
    thread.start()

    async def run_lifespan():
        async with main_module.lifespan(main_module.app):
            while not main_module.running_consumers:
                await asyncio.sleep(0.01)

    asyncio.run(run_lifespan())

    assert stopped.is_set()
    assert not thread.is_alive()
    assert not main_module.consumer_failed.is_set()

