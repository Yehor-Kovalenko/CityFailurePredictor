from datetime import datetime, timezone
from typing import Literal

from pydantic import BaseModel, Field

from AI.Domain.Algorithms.VRPTW import (
    Customer,
    euclidean_distance,
    haversine_distance,
    route_distance,
    route_schedule,
)
from AI.Orchestration.request_manager import Request

DEPOT_ID = "__depot__"

DISTANCES = {
    "euclidean": euclidean_distance,
    "haversine": haversine_distance,
}


class Location(BaseModel):
    x: float
    y: float


class RouteCustomer(BaseModel):
    id: str = Field(min_length=1)
    x: float
    y: float
    demand: float = Field(default=0, ge=0)
    readyTime: float = Field(default=0, ge=0)
    dueTime: float | None = Field(default=None, ge=0)
    serviceTime: float = Field(default=0, ge=0)


class RouteOptimizationRequest(BaseModel):
    """
    Travel time between two points equals their distance, so time windows and
    service times use the unit of the chosen metric: the coordinate unit for
    "euclidean", kilometres for "haversine" (x = longitude, y = latitude).
    """

    depot: Location
    customers: list[RouteCustomer] = Field(min_length=1)
    vehicleCapacity: float | None = Field(default=None, gt=0)
    metric: Literal["euclidean", "haversine"] = "euclidean"
    seed: int | None = None


class RouteStop(BaseModel):
    customerId: str
    arrivalTime: float
    departureTime: float


class VehicleRoute(BaseModel):
    vehicle: int
    distance: float
    load: float
    stops: list[RouteStop]


class RouteOptimizationResponse(BaseModel):
    totalDistance: float
    vehiclesUsed: int
    routes: list[VehicleRoute]


def optimize_routes(request_manager, body: RouteOptimizationRequest) -> RouteOptimizationResponse:
    distance_fn = DISTANCES[body.metric]

    depot = Customer(DEPOT_ID, body.depot.x, body.depot.y, 0, 0, float("inf"), 0)

    customers = [
        Customer(
            c.id, c.x, c.y, c.demand, c.readyTime,
            c.dueTime if c.dueTime is not None else float("inf"),
            c.serviceTime,
        )
        for c in body.customers
    ]

    result = request_manager.handle(Request(
        task="vrp",
        timestamp=datetime.now(timezone.utc),
        data={
            "depot": depot,
            "customers": customers,
            "distance_fn": distance_fn,
            "options": {"vehicle_capacity": body.vehicleCapacity, "seed": body.seed},
        },
    ))

    routes = [
        VehicleRoute(
            vehicle=number,
            distance=route_distance([route], distance_fn),
            load=sum(c.demand for c in route[1:-1]),
            stops=[
                RouteStop(customerId=customer.id, arrivalTime=arrival, departureTime=departure)
                for customer, arrival, departure in route_schedule(route, distance_fn)
            ],
        )
        for number, route in enumerate(result["routes"], start=1)
    ]

    return RouteOptimizationResponse(
        totalDistance=result["cost"],
        vehiclesUsed=len(routes),
        routes=routes,
    )
