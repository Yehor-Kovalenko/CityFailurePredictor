import logging
from AI.Domain.Algorithms.VRPTW import VRPTW_ACO, euclidean_distance

logger = logging.getLogger(__name__)


class InferenceRoutesService:
    def __init__(self, config):
        self.config = config

        optimizer = config["optimizer"]

        logger.info(
            "Routes service initialized | ants=%s iterations=%s capacity=%s",
            optimizer["n_ants"],
            optimizer["iterations"],
            optimizer["vehicle_capacity"],
        )

    def handle_request(self, request):
        depot = request.data["depot"]
        customers = request.data["customers"]

        logger.info(
            "Running VRP | customers=%s depot=(%s,%s)",
            len(customers),
            depot.x,
            depot.y
        )

        solver = VRPTW_ACO(
            depot=depot,
            customers=customers,
            config=self.config["optimizer"],
            distance_fn=euclidean_distance
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