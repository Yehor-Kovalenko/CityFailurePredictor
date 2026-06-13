import logging

from AI.Domain.Algorithms.VRPTW import euclidean_distance, VRPTW_ACO

logger = logging.getLogger(__name__)


class InferenceRoutesService:

    def __init__(self, config):
        self.config = config

        logger.info(
            "Routes service initialized | ants=%s iterations=%s capacity=%s",
            config["n_ants"],
            config["iterations"],
            config["vehicle_capacity"],
        )

    def handle_request(self, request):
        """
        request.data expected:
            {
                "depot": Customer,
                "customers": List[Customer]
            }
        """

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
            config=self.config,
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