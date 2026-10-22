import { afterEach, describe, expect, it, vi } from "vitest";
import { distanceKm, filterWithinRadius, getCurrentPosition } from "@/utils/geo";
import { Incident } from "@/types";

function incidentAt(id: string, lat: number, lng: number): Incident {
  return {
    id,
    incidentTitle: id,
    incidentSummary: "",
    coordinates: { crs: "EPSG:4326", x: String(lat), y: String(lng) },
    incidentType: "WATER",
    status: "OPEN",
    timestamp: "",
    lastUpdated: "",
  };
}

const LODZ = { lat: 51.7592, lng: 19.456 };

describe("geo utils", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("computes great-circle distance", () => {
    const warsaw = { lat: 52.2297, lng: 21.0122 };

    expect(distanceKm(LODZ, LODZ)).toBe(0);
    expect(distanceKm(LODZ, warsaw)).toBeCloseTo(119, 0);
  });

  it("keeps only incidents within the radius", () => {
    const near = incidentAt("near", 51.7650, 19.4600); // ~0.7 km
    const far = incidentAt("far", 51.8500, 19.4600); // ~10 km

    expect(filterWithinRadius([near, far], LODZ, 1).map((i) => i.id)).toEqual(["near"]);
    expect(filterWithinRadius([near, far], LODZ, 11)).toHaveLength(2);
  });

  it("resolves the current position from the browser API", async () => {
    vi.stubGlobal("navigator", {
      geolocation: {
        getCurrentPosition: (ok: PositionCallback) =>
          ok({ coords: { latitude: 51.1, longitude: 19.2 } } as GeolocationPosition),
      },
    });

    await expect(getCurrentPosition()).resolves.toEqual({ lat: 51.1, lng: 19.2 });
  });

  it("rejects when geolocation is unsupported", async () => {
    vi.stubGlobal("navigator", {});

    await expect(getCurrentPosition()).rejects.toThrow("not supported");
  });
});
