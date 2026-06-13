import math
import random
import logging
from copy import deepcopy


logger = logging.getLogger(__name__)


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


def euclidean_distance(a, b):
    return math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2)


# ----------------------------
# TABU / LOCAL SEARCH (simplified)
# ----------------------------

class TabuSearch:
    def __init__(self, max_iterations, tabu_tenure):
        self.max_iterations = max_iterations
        self.tabu_tenure = tabu_tenure

    def optimize(self, routes, depot, capacity, distance_fn):
        best = deepcopy(routes)
        best_cost = distance_fn(best)

        no_improve = 0

        for _ in range(self.max_iterations):
            candidate = self._relocate(best, depot, capacity, distance_fn)
            cost = distance_fn(candidate)

            if cost < best_cost:
                best = candidate
                best_cost = cost
                no_improve = 0
            else:
                no_improve += 1

            if no_improve > self.tabu_tenure:
                break

        logger.info(f"TabuSearch finished | cost={best_cost:.2f}")
        return best

    def _relocate(self, routes, depot, capacity, distance_fn):
        best = deepcopy(routes)
        best_cost = distance_fn(routes)

        for i in range(len(routes)):
            for j in range(1, len(routes[i]) - 1):
                for k in range(len(routes)):
                    if i == k:
                        continue

                    for p in range(1, len(routes[k]) - 1):
                        new_routes = deepcopy(routes)

                        cust = new_routes[i].pop(j)
                        new_routes[k].insert(p, cust)

                        if self._feasible(new_routes[k], capacity):
                            cost = distance_fn(new_routes)
                            if cost < best_cost:
                                best = new_routes
                                best_cost = cost

        return best

    @staticmethod
    def _feasible(route, capacity):
        return sum(c.demand for c in route) <= capacity


# ----------------------------
# ANT
# ----------------------------

class Ant:
    def __init__(self, depot, customers, capacity, pheromones, alpha, beta, urgency_coef):
        self.depot = depot
        self.customers = customers
        self.capacity = capacity
        self.pheromones = pheromones
        self.alpha = alpha
        self.beta = beta
        self.urgency_coef = urgency_coef

        self.routes = []

    def build_solution(self):
        unvisited = self.customers[:]

        routes = []

        while unvisited:
            route = [self.depot]
            load = 0
            time = 0

            while True:
                last = route[-1]

                feasible = [
                    c for c in unvisited
                    if load + c.demand <= self.capacity
                ]

                if not feasible:
                    break

                next_customer = self._select_next(last, feasible, time)

                if next_customer is None:
                    break

                dist = euclidean_distance(last, next_customer)

                time = max(time + dist, next_customer.ready) + next_customer.service
                load += next_customer.demand

                route.append(next_customer)
                unvisited.remove(next_customer)

            route.append(self.depot)
            routes.append(route)

        self.routes = routes
        return routes

    def _select_next(self, last, feasible, current_time):
        scores = []
        total = 0

        for c in feasible:
            pher = self.pheromones[last.id][c.id] ** self.alpha
            heuristic = 1 / (euclidean_distance(last, c) + 1)

            urgency = 1 / (max(1, c.due - current_time) + 1)

            score = pher * ((heuristic + self.urgency_coef * urgency) ** self.beta)

            scores.append((c, score))
            total += score

        if total == 0:
            return random.choice(feasible)

        r = random.random()
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
        self.distance_fn = distance_fn

        self.capacity = config["vehicle_capacity"]
        self.n_ants = config["n_ants"]
        self.iterations = config["iterations"]
        self.evaporation = config["evaporation"]
        self.alpha = config["alpha"]
        self.beta = config["beta"]
        self.urgency_coef = config["coef_urgency"]

        self.tabu = TabuSearch(
            config["tabu_max_iterations"],
            config["tabu_tenure"]
        )

        self.pheromones = self._init_pheromones()

    # ----------------------------
    # PUBLIC API
    # ----------------------------

    def run(self):
        best_routes = None
        best_cost = float("inf")

        stagnation = 0

        for it in range(self.iterations):
            ants = self._spawn_ants()

            iteration_best = float("inf")

            for ant in ants:
                routes = ant.build_solution()
                cost = self.distance_fn(routes)

                if cost < best_cost:
                    best_cost = cost
                    best_routes = routes
                    stagnation = 0
                else:
                    stagnation += 1

                iteration_best = min(iteration_best, cost)

            self._evaporate()
            self._deposit(ants, best_cost)

            if stagnation > 10:
                logger.info("Activating Tabu Search due to stagnation")
                best_routes = self.tabu.optimize(
                    best_routes, self.depot, self.capacity, self.distance_fn
                )
                best_cost = self.distance_fn(best_routes)
                stagnation = 0

            logger.info(f"Iter {it} | best={best_cost:.2f}")

        return best_routes, best_cost

    # ----------------------------
    # INTERNALS
    # ----------------------------

    def _spawn_ants(self):
        return [
            Ant(
                self.depot,
                self.customers,
                self.capacity,
                self.pheromones,
                self.alpha,
                self.beta,
                self.urgency_coef
            )
            for _ in range(self.n_ants)
        ]

    def _init_pheromones(self):
        nodes = [self.depot] + self.customers

        pher = {}
        for i in nodes:
            pher[i.id] = {}
            for j in nodes:
                pher[i.id][j.id] = 1.0
        return pher

    def _evaporate(self):
        for i in self.pheromones:
            for j in self.pheromones[i]:
                self.pheromones[i][j] *= (1 - self.evaporation)

    def _deposit(self, ants, best_cost):
        for ant in ants:
            cost = self.distance_fn(ant.routes)
            if cost <= 0:
                continue

            deposit = 1 / cost

            for route in ant.routes:
                for i in range(len(route) - 1):
                    a, b = route[i].id, route[i + 1].id
                    self.pheromones[a][b] += deposit


