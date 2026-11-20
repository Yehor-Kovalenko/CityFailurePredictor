import random

import pytest

from AI.Domain.Algorithms.VRPTW import (
    Customer,
    InfeasibleInstanceError,
    TabuSearch,
    VRPTW_ACO,
    euclidean_distance,
    route_distance,
    route_is_feasible,
    two_opt,
)

CONFIG = {
    "vehicle_capacity": 50,
    "n_ants": 10,
    "iterations": 15,
    "evaporation": 0.5,
    "alpha": 1.0,
    "beta": 2.0,
    "coef_urgency": 2.0,
    "tabu_max_iterations": 30,
    "tabu_tenure": 10,
    "stagnation_limit": 3,
    "seed": 7,
}


def depot():
    return Customer(0, 50, 50, 0, 0, 10_000, 0)


def random_customers(n, seed=1, tight_windows=False):
    rng = random.Random(seed)
    customers = []

    for i in range(1, n + 1):
        ready = rng.randint(0, 200) if tight_windows else 0
        due = ready + rng.randint(150, 400) if tight_windows else 10_000
        customers.append(
            Customer(i, rng.uniform(0, 100), rng.uniform(0, 100), rng.randint(1, 10), ready, due, 5)
        )

    return customers


def solve(customers, **overrides):
    return VRPTW_ACO(depot(), customers, {**CONFIG, **overrides}, euclidean_distance).run()


def assert_valid_solution(routes, customers, capacity):
    visited = [c.id for route in routes for c in route[1:-1]]

    assert sorted(visited) == sorted(c.id for c in customers)

    for route in routes:
        assert route[0].id == 0 and route[-1].id == 0
        assert route_is_feasible(route, capacity)


def test_demand_above_capacity_is_rejected():
    customers = [Customer(1, 10, 10, 5, 0, 1000, 1), Customer(2, 20, 20, 51, 0, 1000, 1)]

    with pytest.raises(InfeasibleInstanceError, match=r"\[2\]"):
        solve(customers)


def test_unreachable_time_window_is_rejected():
    customers = [Customer(1, 10, 10, 5, 0, 1000, 1), Customer(2, 100, 100, 5, 0, 3, 1)]

    with pytest.raises(InfeasibleInstanceError, match=r"\[2\]"):
        solve(customers)


def test_duplicate_ids_are_rejected():
    customers = [Customer(1, 10, 10, 5, 0, 1000, 1), Customer(1, 20, 20, 5, 0, 1000, 1)]

    with pytest.raises(InfeasibleInstanceError):
        solve(customers)


def test_no_customers_gives_empty_plan():
    assert solve([]) == ([], 0.0)


@pytest.mark.parametrize("tight_windows", [False, True])
def test_every_customer_is_served_once_within_constraints(tight_windows):
    customers = random_customers(30, tight_windows=tight_windows)

    routes, cost = solve(customers)

    assert_valid_solution(routes, customers, CONFIG["vehicle_capacity"])
    assert cost == pytest.approx(route_distance(routes))


def test_same_seed_gives_same_plan():
    customers = random_customers(20)

    first_routes, first_cost = solve(customers)
    second_routes, second_cost = solve(customers)

    assert first_cost == second_cost
    assert [[c.id for c in r] for r in first_routes] == [[c.id for c in r] for r in second_routes]


def test_two_opt_removes_crossing():
    d = Customer(0, 0, 0, 0, 0, 1000, 0)
    a, b, c, e = (
        Customer(1, 0, 10, 1, 0, 1000, 0),
        Customer(2, 10, 0, 1, 0, 1000, 0),
        Customer(3, 10, 10, 1, 0, 1000, 0),
        Customer(4, 0, 5, 1, 0, 1000, 0),
    )
    crossed = [d, a, b, c, e, d]

    improved = two_opt(crossed, capacity=10)

    assert route_distance([improved]) < route_distance([crossed])
    assert sorted(x.id for x in improved) == sorted(x.id for x in crossed)


def test_two_opt_keeps_time_windows():
    d = Customer(0, 0, 0, 0, 0, 1000, 0)
    far_first = Customer(1, 10, 0, 1, 0, 11, 0)
    near_later = Customer(2, 1, 0, 1, 15, 1000, 0)
    route = [d, far_first, near_later, d]

    assert two_opt(route, capacity=10) == route


def test_tabu_search_never_returns_worse_or_infeasible_plan():
    customers = random_customers(30, tight_windows=True)
    capacity = CONFIG["vehicle_capacity"]
    routes, _ = solve(customers, iterations=1, n_ants=1)

    improved = TabuSearch(max_iterations=30, tabu_tenure=5).optimize(routes, capacity)

    assert route_distance(improved) <= route_distance(routes) + 1e-9
    assert_valid_solution(improved, customers, capacity)
    assert_valid_solution(routes, customers, capacity)
