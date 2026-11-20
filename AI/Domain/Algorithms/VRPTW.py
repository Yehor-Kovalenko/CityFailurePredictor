import logging
import math
import random

logger = logging.getLogger(__name__)

EPS = 1e-9
MIN_PHEROMONE = 1e-6


# ----------------------------
# DOMAIN MODEL
# ----------------------------

class Customer:
    def __init__(self, cust_id, x, y, demand, ready, due, service):
        self.id = cust_id
        self.x = x
        self.y = y
        self.demand = demand
        self.ready = ready
        self.due = due
        self.service = service


class InfeasibleInstanceError(ValueError):
    pass


def euclidean_distance(a, b):
    return math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2)


def route_distance(routes, distance_fn=euclidean_distance):
    total = 0.0
    for route in routes:
        for i in range(len(route) - 1):
            total += distance_fn(route[i], route[i + 1])
    return total


def route_is_feasible(route, capacity, distance_fn=euclidean_distance):
    """route = [depot, c1, ..., ck, depot]; checks capacity and time windows."""
    load = 0
    current_time = 0.0

    for prev, customer in zip(route, route[1:-1]):
        load += customer.demand
        if load > capacity:
            return False

        arrival = max(current_time + distance_fn(prev, customer), customer.ready)
        if arrival > customer.due:
            return False

        current_time = arrival + customer.service

    return True


def route_schedule(route, distance_fn=euclidean_distance):
    """Arrival and departure time at every customer of route = [depot, c1, ..., ck, depot]."""
    schedule = []
    current_time = 0.0

    for prev, customer in zip(route, route[1:-1]):
        arrival = max(current_time + distance_fn(prev, customer), customer.ready)
        current_time = arrival + customer.service

        schedule.append((customer, arrival, current_time))

    return schedule


def haversine_distance(a, b):
    """Great-circle distance in kilometres; x is longitude and y is latitude, in degrees."""
    lon1, lat1, lon2, lat2 = map(math.radians, (a.x, a.y, b.x, b.y))

    h = (
        math.sin((lat2 - lat1) / 2) ** 2
        + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    )

    return 2 * 6371.0088 * math.asin(math.sqrt(h))


def validate_instance(depot, customers, capacity, distance_fn=euclidean_distance):
    ids = [c.id for c in customers]

    if len(set(ids)) != len(ids) or depot.id in ids:
        raise InfeasibleInstanceError("Customer ids must be unique and differ from the depot id")

    too_heavy = [c.id for c in customers if c.demand > capacity]
    if too_heavy:
        raise InfeasibleInstanceError(
            f"Demand exceeds vehicle capacity {capacity} for customers {too_heavy}"
        )

    unreachable = [
        c.id for c in customers
        if max(distance_fn(depot, c), c.ready) > c.due
    ]
    if unreachable:
        raise InfeasibleInstanceError(
            f"Time window cannot be met even on a direct trip from the depot for customers {unreachable}"
        )


# ----------------------------
# 2-OPT
# ----------------------------
def two_opt(route, capacity, distance_fn=euclidean_distance):
    """Reverses segments of a single route while it gets shorter and stays feasible."""
    best = route
    improved = True

    while improved:
        improved = False

        for i in range(1, len(best) - 2):
            for j in range(i + 1, len(best) - 1):
                delta = (
                    distance_fn(best[i - 1], best[j]) + distance_fn(best[i], best[j + 1])
                    - distance_fn(best[i - 1], best[i]) - distance_fn(best[j], best[j + 1])
                )

                if delta >= -EPS:
                    continue

                candidate = best[:i] + best[i:j + 1][::-1] + best[j + 1:]

                if route_is_feasible(candidate, capacity, distance_fn):
                    best = candidate
                    improved = True

    return best


# ----------------------------
# TABU SEARCH
# ----------------------------
class TabuSearch:
    """Relocate neighbourhood: move one customer to another route.

    The best admissible move is applied even when it makes the solution worse,
    and the moved customer is tabu for `tabu_tenure` iterations unless the move
    beats the best solution found so far.
    """

    def __init__(self, max_iterations=50, tabu_tenure=10):
        self.max_iterations = max_iterations
        self.tabu_tenure = tabu_tenure

    def optimize(self, routes, capacity, distance_fn=euclidean_distance):
        current = [route[:] for route in routes]
        current_cost = route_distance(current, distance_fn)

        best = [route[:] for route in current]
        best_cost = current_cost

        tabu_until = {}
        no_improve = 0

        for iteration in range(self.max_iterations):
            move = self._best_move(
                current, capacity, distance_fn, tabu_until, iteration, current_cost, best_cost
            )

            if move is None:
                break

            delta, i, j, k, p = move

            customer = current[i].pop(j)
            current[k].insert(p, customer)
            current = [route for route in current if len(route) > 2]
            current_cost += delta

            tabu_until[customer.id] = iteration + self.tabu_tenure

            if current_cost < best_cost - EPS:
                best = [route[:] for route in current]
                best_cost = current_cost
                no_improve = 0
            else:
                no_improve += 1

            if no_improve > self.tabu_tenure:
                break

        return best

    def _best_move(self, routes, capacity, distance_fn, tabu_until, iteration, current_cost, best_cost):
        loads = [sum(c.demand for c in route[1:-1]) for route in routes]
        candidates = []

        for i, source in enumerate(routes):
            for j in range(1, len(source) - 1):
                customer = source[j]
                removal_gain = (
                    distance_fn(source[j - 1], customer) + distance_fn(customer, source[j + 1])
                    - distance_fn(source[j - 1], source[j + 1])
                )

                for k, target in enumerate(routes):
                    if k == i or loads[k] + customer.demand > capacity:
                        continue

                    for p in range(1, len(target)):
                        insertion_cost = (
                            distance_fn(target[p - 1], customer) + distance_fn(customer, target[p])
                            - distance_fn(target[p - 1], target[p])
                        )
                        candidates.append((insertion_cost - removal_gain, i, j, k, p))

        candidates.sort(key=lambda move: move[0])

        for delta, i, j, k, p in candidates:
            customer = routes[i][j]

            is_tabu = tabu_until.get(customer.id, -1) > iteration
            if is_tabu and current_cost + delta >= best_cost - EPS:
                continue

            new_target = routes[k][:p] + [customer] + routes[k][p:]
            new_source = routes[i][:j] + routes[i][j + 1:]

            if (
                route_is_feasible(new_target, capacity, distance_fn)
                and route_is_feasible(new_source, capacity, distance_fn)
            ):
                return delta, i, j, k, p

        return None


# ----------------------------
# ANT
# ----------------------------
class Ant:
    def __init__(
        self, depot, customers, capacity, pheromones, alpha, beta, urgency_coef,
        distance_fn=euclidean_distance, rng=random,
    ):
        self.depot = depot
        self.customers = customers
        self.capacity = capacity
        self.pheromones = pheromones
        self.alpha = alpha
        self.beta = beta
        self.urgency_coef = urgency_coef
        self.distance_fn = distance_fn
        self.rng = rng

        self.routes = []

    def build_solution(self):
        unvisited = self.customers[:]
        routes = []

        while unvisited:
            route = [self.depot]
            load = 0
            current_time = 0

            while True:
                last = route[-1]

                feasible = []
                for c in unvisited:
                    if load + c.demand > self.capacity:
                        continue

                    arrival = max(current_time + self.distance_fn(last, c), c.ready)

                    if arrival > c.due:
                        continue

                    feasible.append(c)

                if not feasible:
                    break

                next_customer = self._select_next(last, feasible, current_time)

                dist = self.distance_fn(last, next_customer)

                current_time = max(current_time + dist, next_customer.ready)
                current_time += next_customer.service
                load += next_customer.demand

                route.append(next_customer)
                unvisited.remove(next_customer)

            if len(route) == 1:
                raise InfeasibleInstanceError(
                    f"Customers {[c.id for c in unvisited]} cannot be served by an empty vehicle"
                )

            route.append(self.depot)
            routes.append(route)

        self.routes = routes
        return routes

    def _select_next(self, last, feasible, current_time):
        scores = []
        total = 0

        for c in feasible:
            pher = self.pheromones[last.id][c.id] ** self.alpha
            heuristic = 1 / (self.distance_fn(last, c) + 1)
            urgency = 1 / (max(1, c.due - current_time) + 1)

            score = pher * ((heuristic + self.urgency_coef * urgency) ** self.beta)

            scores.append((c, score))
            total += score

        if total == 0:
            return self.rng.choice(feasible)

        r = self.rng.random()
        acc = 0

        for c, s in scores:
            acc += s / total
            if r <= acc:
                return c

        return scores[-1][0]


# ----------------------------
# ACO CORE
# ----------------------------
class VRPTW_ACO:
    def __init__(self, depot, customers, config, distance_fn):
        self.depot = depot
        self.customers = customers

        self.capacity = config["vehicle_capacity"]
        self.n_ants = config["n_ants"]
        self.iterations = config["iterations"]
        self.evaporation = config["evaporation"]
        self.alpha = config["alpha"]
        self.beta = config["beta"]
        self.urgency_coef = config["coef_urgency"]
        self.stagnation_limit = config.get("stagnation_limit", 10)
        self.rng = random.Random(config.get("seed"))

        self.tabu = TabuSearch(
            config["tabu_max_iterations"],
            config["tabu_tenure"]
        )

        validate_instance(depot, customers, self.capacity, distance_fn)

        nodes = [depot] + customers

        matrix = {a.id: {b.id: distance_fn(a, b) for b in nodes} for a in nodes}
        self.distance_fn = lambda a, b: matrix[a.id][b.id]

        self.pheromones = {
            i.id: {j.id: 1.0 for j in nodes}
            for i in nodes
        }

    def run(self):
        if not self.customers:
            return [], 0.0

        best_routes = None
        best_cost = float("inf")
        stagnation = 0

        for it in range(self.iterations):
            ants = self._spawn_ants()
            improved = False

            for ant in ants:
                ant.routes = [
                    two_opt(route, self.capacity, self.distance_fn)
                    for route in ant.build_solution()
                ]
                cost = route_distance(ant.routes, self.distance_fn)

                if cost < best_cost - EPS:
                    best_cost = cost
                    best_routes = ant.routes
                    improved = True

            self._evaporate()
            self._deposit(ants)

            stagnation = 0 if improved else stagnation + 1

            if stagnation > self.stagnation_limit:
                logger.info("Tabu search triggered")

                best_routes = self.tabu.optimize(best_routes, self.capacity, self.distance_fn)
                best_cost = route_distance(best_routes, self.distance_fn)
                stagnation = 0

            logger.debug("Iter %s | best=%.2f", it, best_cost)

        return best_routes, best_cost

    def _spawn_ants(self):
        return [
            Ant(
                self.depot,
                self.customers,
                self.capacity,
                self.pheromones,
                self.alpha,
                self.beta,
                self.urgency_coef,
                self.distance_fn,
                self.rng,
            )
            for _ in range(self.n_ants)
        ]

    def _evaporate(self):
        for i in self.pheromones:
            for j in self.pheromones[i]:
                self.pheromones[i][j] = max(
                    self.pheromones[i][j] * (1 - self.evaporation),
                    MIN_PHEROMONE,
                )

    def _deposit(self, ants):
        for ant in ants:
            cost = route_distance(ant.routes, self.distance_fn)

            if cost <= 0:
                continue

            deposit = 1 / cost

            for route in ant.routes:
                for i in range(len(route) - 1):
                    a = route[i].id
                    b = route[i + 1].id

                    self.pheromones[a][b] += deposit
