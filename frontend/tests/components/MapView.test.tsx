import {act, fireEvent, render, screen} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {MapView} from "@/components/MapView";
import {Incident} from "@/types";
import {stubLeafletLayout} from "../leafletLayout";

const CONSENT_KEY = "city-failure-predictor.geo-consent";

function incidentAt(id: string, lat: number, lng: number, status: Incident["status"] = "OPEN"): Incident {
    return {
        id,
        incidentTitle: `Incident ${id}`,
        incidentSummary: "Summary",
        coordinates: {crs: "EPSG:4326", x: String(lat), y: String(lng)},
        incidentType: "WATER",
        status,
        timestamp: "",
        lastUpdated: "",
    };
}

const INCIDENTS = [
    incidentAt("near", 51.7605, 19.4605, "OPEN"),
    incidentAt("mid", 51.7700, 19.4600, "IN_PROGRESS"),
    incidentAt("far", 51.8500, 19.4600, "RESOLVED"),
];

let onPosition: PositionCallback | undefined;

function stubGeolocation() {
    vi.stubGlobal("navigator", {
        ...navigator,
        geolocation: {
            watchPosition: (ok: PositionCallback) => {
                onPosition = ok;
                return 1;
            },
            clearWatch: vi.fn(),
        },
    });
}

function renderMap(props: Partial<Parameters<typeof MapView>[0]> = {}) {
    const onSelectIncident = vi.fn();
    const view = render(
        <MapView incidents={INCIDENTS} selectedIncident={null} onSelectIncident={onSelectIncident} {...props} />,
    );
    return {...view, onSelectIncident};
}

const markers = (container: HTMLElement) =>
    container.querySelectorAll(".custom-incident-marker");

describe("MapView", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        onPosition = undefined;
    });

    it("shows every incident and asks for consent by default", () => {
        const {container} = renderMap();

        expect(markers(container)).toHaveLength(3);
        expect(screen.getByText("Show incidents near you?")).toBeInTheDocument();
    });

    it("shows only incidents near the user's position after consent", async () => {
        const user = userEvent.setup();
        stubGeolocation();
        const {container} = renderMap();

        await user.click(screen.getByRole("button", {name: "Use my location"}));
        act(() => onPosition?.({coords: {latitude: 51.76, longitude: 19.46}} as GeolocationPosition));

        expect(markers(container)).toHaveLength(2);
        expect(screen.getByText("2 incidents in range")).toBeInTheDocument();

        await user.selectOptions(screen.getByLabelText("Radius"), "1");
        expect(markers(container)).toHaveLength(1);
    });

    it("filters around a point picked on the map when consent is refused", async () => {
        const user = userEvent.setup();
        localStorage.setItem(CONSENT_KEY, "denied");
        stubLeafletLayout();
        const {container, rerender} = renderMap({incidents: []});

        expect(screen.getByText("Click the map to choose an area")).toBeInTheDocument();

        fireEvent.click(container.querySelector(".leaflet-container")!, {clientX: 0, clientY: 0});
        rerender(<MapView incidents={INCIDENTS} selectedIncident={null} onSelectIncident={vi.fn()}/>);

        expect(screen.getByText("Incidents in the selected area")).toBeInTheDocument();
        expect(markers(container)).toHaveLength(2);

        await user.click(screen.getByRole("button", {name: "Clear area"}));
        expect(markers(container)).toHaveLength(3);
    });

    it("selects and deselects an incident by clicking its marker", () => {
        const {container, onSelectIncident, rerender} = renderMap();

        fireEvent.click(markers(container)[0]);
        expect(onSelectIncident).toHaveBeenCalledWith(INCIDENTS[0]);

        rerender(
            <MapView incidents={INCIDENTS} selectedIncident={INCIDENTS[0]} onSelectIncident={onSelectIncident}/>,
        );
        fireEvent.click(markers(container)[0]);
        expect(onSelectIncident).toHaveBeenLastCalledWith(null);
    });
});
