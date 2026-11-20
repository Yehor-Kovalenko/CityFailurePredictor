from pathlib import Path

import pytest
from pydantic import ValidationError

from AI.Domain.Algorithms.VRPTW import InfeasibleInstanceError, haversine_distance, Customer
from AI.Orchestration.request_manager import RequestManager
from AI.Orchestration.routes_api import RouteOptimizationRequest, optimize_routes


@pytest.fixture(scope="module")
def manager():
    return RequestManager()


def body(customers, **extra):
    return RouteOptimizationRequest(depot={"x": 0, "y": 0}, customers=customers, seed=1, **extra)


def customer(i, x, y, **extra):
    return {"id": f"c{i}", "x": x, "y": y, **extra}


def test_every_customer_is_visited_once_with_consistent_totals(manager):
    customers = [customer(i, x, y, demand=10) for i, (x, y) in enumerate([(1, 0), (2, 0), (0, 3), (-2, 1), (5, 5)])]

    response = optimize_routes(manager, body(customers))

    visited = [stop.customerId for route in response.routes for stop in route.stops]
    assert sorted(visited) == ["c0", "c1", "c2", "c3", "c4"]
    assert response.vehiclesUsed == len(response.routes)
    assert response.totalDistance == pytest.approx(sum(route.distance for route in response.routes))
    assert [route.vehicle for route in response.routes] == list(range(1, len(response.routes) + 1))


def test_capacity_from_request_splits_the_load(manager):
    customers = [customer(i, i + 1, 0, demand=6) for i in range(4)]

    response = optimize_routes(manager, body(customers, vehicleCapacity=12))

    assert response.vehiclesUsed == 2
    assert all(route.load <= 12 for route in response.routes)


def test_stops_respect_time_windows_and_service_time(manager):
    customers = [
        customer(1, 3, 0, readyTime=10, dueTime=20, serviceTime=2),
        customer(2, 6, 0, readyTime=0, dueTime=40, serviceTime=1),
    ]

    response = optimize_routes(manager, body(customers))

    stops = {stop.customerId: stop for route in response.routes for stop in route.stops}
    assert 10 <= stops["c1"].arrivalTime <= 20
    assert stops["c1"].departureTime == stops["c1"].arrivalTime + 2
    assert stops["c2"].arrivalTime <= 40


def test_same_seed_gives_same_answer(manager):
    customers = [customer(i, (i * 7) % 11, (i * 5) % 13, demand=3) for i in range(12)]

    first = optimize_routes(manager, body(customers))
    second = optimize_routes(manager, body(customers))

    assert first == second


def test_haversine_metric_reports_kilometres(manager):
    lodz = customer(1, 19.4560, 51.7592)

    response = optimize_routes(
        manager,
        RouteOptimizationRequest(depot={"x": 21.0122, "y": 52.2297}, customers=[lodz], metric="haversine"),
    )

    assert response.totalDistance == pytest.approx(2 * 118.8, rel=0.02)
    assert haversine_distance(Customer(0, 0, 0, 0, 0, 0, 0), Customer(1, 0, 1, 0, 0, 0, 0)) == pytest.approx(111.2, rel=0.01)


def test_unservable_customer_is_reported(manager):
    with pytest.raises(InfeasibleInstanceError, match="c1"):
        optimize_routes(manager, body([customer(1, 1, 1, demand=500)]))


def test_too_many_customers_are_rejected(manager):
    customers = [customer(i, i, i) for i in range(151)]

    with pytest.raises(InfeasibleInstanceError, match="Too many customers"):
        optimize_routes(manager, body(customers))


@pytest.mark.parametrize("payload", [
    {"depot": {"x": 0, "y": 0}, "customers": []},
    {"depot": {"x": 0, "y": 0}, "customers": [{"id": "a", "x": 1, "y": 1, "demand": -1}]},
    {"depot": {"x": 0, "y": 0}, "customers": [{"id": "a", "x": 1, "y": 1}], "metric": "manhattan"},
    {"depot": {"x": 0, "y": 0}, "customers": [{"id": "a", "x": 1, "y": 1}], "vehicleCapacity": 0},
])
def test_invalid_requests_fail_validation(payload):
    with pytest.raises(ValidationError):
        RouteOptimizationRequest(**payload)


def test_endpoint_turns_infeasible_instance_into_422(monkeypatch):
    from fastapi import HTTPException

    monkeypatch.chdir(Path(__file__).resolve().parents[2])

    from AI import main

    with pytest.raises(HTTPException) as error:
        main.routes_optimize(body([customer(1, 1, 1, demand=500)]))

    assert error.value.status_code == 422
    assert "c1" in error.value.detail

    ok = main.routes_optimize(body([customer(1, 1, 1, demand=5)]))
    assert ok.vehiclesUsed == 1
