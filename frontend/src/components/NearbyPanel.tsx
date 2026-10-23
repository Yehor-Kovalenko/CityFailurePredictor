import { Crosshair, MapPin } from "lucide-react";
import { GeoConsent } from "../hooks/useGeolocation";

const RADIUS_OPTIONS_KM = [1, 2, 3, 5, 10];

interface NearbyPanelProps {
  consent: GeoConsent;
  hasArea: boolean;
  visibleCount: number;
  radiusKm: number;
  error: string | null;
  onRadiusChange: (radiusKm: number) => void;
  onGrant: () => void;
  onDeny: () => void;
  onClearArea: () => void;
}

export function NearbyPanel({
  consent,
  hasArea,
  visibleCount,
  radiusKm,
  error,
  onRadiusChange,
  onGrant,
  onDeny,
  onClearArea,
}: NearbyPanelProps) {
  return (
    <div className="absolute top-3 right-3 z-[1000] w-72 max-w-[calc(100%-1.5rem)] rounded-lg border border-dark-600 bg-dark-900/95 p-3 text-sm text-gray-200 shadow-lg">
      {consent === "unknown" ? (
        <>
          <p className="font-semibold text-white">Show incidents near you?</p>
          <p className="mt-1 text-xs text-gray-400">
            Your position is used only in this browser to filter the map. It is
            never sent to the server or saved.
          </p>
          <div className="mt-3 flex gap-2">
            <button
              onClick={onGrant}
              className="flex flex-1 items-center justify-center gap-1 rounded-md bg-blue-600 px-3 py-2 font-medium text-white hover:bg-blue-700"
            >
              <Crosshair size={16} />
              Use my location
            </button>
            <button
              onClick={onDeny}
              className="flex-1 rounded-md bg-dark-700 px-3 py-2 font-medium hover:bg-dark-600"
            >
              Not now
            </button>
          </div>
        </>
      ) : (
        <>
          <p className="flex items-center gap-1 font-semibold text-white">
            <MapPin size={16} />
            {consent === "granted"
              ? "Incidents near your location"
              : hasArea
                ? "Incidents in the selected area"
                : "Click the map to choose an area"}
          </p>

          <label className="mt-2 flex items-center justify-between gap-2">
            <span className="text-gray-400">Radius</span>
            <select
              aria-label="Radius"
              value={radiusKm}
              onChange={(e) => onRadiusChange(Number(e.target.value))}
              className="rounded-md border-dark-600 bg-dark-800 py-1 text-gray-200"
            >
              {RADIUS_OPTIONS_KM.map((km) => (
                <option key={km} value={km}>
                  {km} km
                </option>
              ))}
            </select>
          </label>

          {hasArea && (
            <p className="mt-2 text-xs text-gray-400">
              {visibleCount} incident{visibleCount === 1 ? "" : "s"} in range
            </p>
          )}

          <div className="mt-3 flex gap-2">
            {consent === "granted" ? (
              <button
                onClick={onDeny}
                className="flex-1 rounded-md bg-dark-700 px-3 py-2 font-medium hover:bg-dark-600"
              >
                Stop using location
              </button>
            ) : (
              <>
                <button
                  onClick={onGrant}
                  className="flex flex-1 items-center justify-center gap-1 rounded-md bg-blue-600 px-3 py-2 font-medium text-white hover:bg-blue-700"
                >
                  <Crosshair size={16} />
                  Use my location
                </button>
                {hasArea && (
                  <button
                    onClick={onClearArea}
                    className="flex-1 rounded-md bg-dark-700 px-3 py-2 font-medium hover:bg-dark-600"
                  >
                    Clear area
                  </button>
                )}
              </>
            )}
          </div>
        </>
      )}

      {error && <p className="mt-2 text-xs text-red-400">{error}</p>}
    </div>
  );
}
