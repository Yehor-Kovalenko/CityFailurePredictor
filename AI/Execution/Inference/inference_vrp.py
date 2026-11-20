import logging
from AI.Domain.Algorithms.VRPTW import VRPTW_ACO, InfeasibleInstanceError, euclidean_distance

logger = logging.getLogger(__name__)

OVERRIDABLE_OPTIONS = ("vehicle_capacity", "seed")


class InferenceRoutesService:
    def __init__(self, config):
        self.config = config

        optimizer = config["optimizer"]

        self.max_customers = config.get("limits", {}).get("max_customers")

        logger.info(
            "Routes service initialized | ants=%s iterations=%s capacity=%s",
            optimizer["n_ants"],
            optimizer["iterations"],
            optimizer["vehicle_capacity"],
        )

    def handle_request(self, request):
        """
        request.data: depot, customers and optionally
        - options: per-request values for vehicle_capacity and seed,
        - distance_fn: distance between two points (default: euclidean).
        """
        depot = request.data["depot"]
        customers = request.data["customers"]
        distance_fn = request.data.get("distance_fn") or euclidean_distance

        if self.max_customers is not None and len(customers) > self.max_customers:
            raise InfeasibleInstanceError(
                f"Too many customers: {len(customers)} (limit {self.max_customers})"
            )

        optimizer = dict(self.config["optimizer"])

        for key, value in (request.data.get("options") or {}).items():
            if key in OVERRIDABLE_OPTIONS and value is not None:
                optimizer[key] = value

        logger.info(
            "Running VRP | customers=%s depot=(%s,%s)",
            len(customers),
            depot.x,
            depot.y
        )

        solver = VRPTW_ACO(
            depot=depot,
            customers=customers,
            config=optimizer,
            distance_fn=distance_fn
        )

        routes, cost = solver.run()

        logger.info(
            "VRP completed | cost=%.2f routes=%s",
            cost,
            len(routes)
        )

        return {
            "routes": routes,
            "cost": cost
        }
