import numpy as np
import pandas as pd
import pytest
from datetime import datetime, timedelta

from AI.Domain.Algorithms.VRPTW import Customer
from AI.Orchestration.request_manager import RequestManager, Request


# =========================================================
# FIXTURE
# =========================================================
@pytest.fixture(scope="module")
def manager():
    return RequestManager()


# =========================================================
# ELECTRICITY TEST
# =========================================================
def test_electricity(manager):

    values = np.random.rand(24)

    request = Request(
        task="electricity",
        timestamp=datetime.utcnow(),
        data=values,
    )

    result = manager.handle(request)

    assert isinstance(result, dict)
    assert "next_prediction" in result
    assert isinstance(result["next_prediction"], float)

    assert np.isfinite(result["next_prediction"])


# =========================================================
# TRAFFIC TEST
# =========================================================
def test_traffic(manager):

    now = pd.Timestamp.utcnow().floor("h")
    n = 72

    df = pd.DataFrame({
        "DateTime": pd.date_range(end=now, periods=n, freq="h"),
        "Vehicles": np.random.randint(0, 100, n),
        "Junction": np.ones(n, dtype=int)
    })

    request = Request(
        task="traffic",
        timestamp=datetime.utcnow(),
        data=df,
    )

    result = manager.handle(request)

    assert isinstance(result, dict)

    assert "next_prediction" in result
    assert "forecast" in result

    assert isinstance(result["next_prediction"], float)
    assert isinstance(result["forecast"], list)

    assert len(result["forecast"]) > 0

    # structural checks (important for production stability)
    assert all(isinstance(x, (int, float, np.floating)) for x in result["forecast"])
    assert np.isfinite(result["next_prediction"])

# =========================================================
# VRP TEST
# =========================================================
def test_vrp(manager):

    from AI.Domain.Algorithms.VRPTW import Customer

    n = 10

    coords = [(np.random.rand(), np.random.rand()) for _ in range(n)]
    demands = np.random.randint(1, 10, n).tolist()

    customers = [
        Customer(
            cust_id=i,
            x=coords[i][0],
            y=coords[i][1],
            demand=demands[i],
            ready=0,
            due=1000,
            service=10
        )
        for i in range(n)
    ]

    depot = customers[0]

    request = Request(
        task="vrp",
        timestamp=datetime.utcnow(),
        data={
            "depot": depot,
            "customers": customers[1:],
        },
    )

    result = manager.handle(request)

    assert isinstance(result, dict)

    assert "routes" in result
    assert "cost" in result

    assert isinstance(result["routes"], list)
    assert isinstance(result["cost"], float)

    assert len(result["routes"]) > 0

    # each route must be valid structure
    for route in result["routes"]:
        assert isinstance(route, list)
        assert len(route) >= 2  # depot -> ... -> depot

        # must start and end at depot
        assert route[0].id == depot.id
        assert route[-1].id == depot.id

    # cost sanity check
    assert result["cost"] > 0
    assert np.isfinite(result["cost"])

# =========================================================
# INVALID TASK TEST
# =========================================================
def test_invalid_task(manager):

    request = Request(
        task="invalid_task",
        timestamp=datetime.utcnow(),
        data=None,
    )

    with pytest.raises(ValueError):
        manager.handle(request)