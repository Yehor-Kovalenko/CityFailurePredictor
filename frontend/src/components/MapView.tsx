import { useRef, useEffect } from "react";
import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import L from "leaflet";
import { Incident } from "../types";
import { INCIDENT_TYPE_ICONS } from "../constants";

delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl:
    "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png",
  iconUrl:
    "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png",
  shadowUrl:
    "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png",
});

interface MapViewProps {
  incidents: Incident[];
  selectedIncident: Incident | null;
  onSelectIncident: (incident: Incident | null) => void;
}

function MapContent({
  incidents,
  selectedIncident,
  onSelectIncident,
}: MapViewProps) {
  const map = useMap();
  const markerRefs = useRef<Record<string, L.Marker>>({});

  useEffect(() => {
    const refreshMapSize = () => map.invalidateSize({ pan: false });
    refreshMapSize();
    const timeoutId = window.setTimeout(refreshMapSize, 150);

    window.addEventListener("resize", refreshMapSize);
    return () => {
      window.clearTimeout(timeoutId);
      window.removeEventListener("resize", refreshMapSize);
    };
  }, [map]);

  useEffect(() => {
    if (incidents.length > 0) {
      const lats = incidents.map((i) => parseFloat(i.coordinates.x));
      const lngs = incidents.map((i) => parseFloat(i.coordinates.y));
      const maxLat = Math.max(...lats);
      const minLat = Math.min(...lats);
      const maxLng = Math.max(...lngs);
      const minLng = Math.min(...lngs);

      const bounds = L.latLngBounds([minLat, minLng], [maxLat, maxLng]);
      map.fitBounds(bounds, { padding: [50, 50] });
    }
  }, [incidents, map]);

  useEffect(() => {
    if (selectedIncident && markerRefs.current[selectedIncident.id]) {
      markerRefs.current[selectedIncident.id].openPopup();
    }
  }, [selectedIncident]);

  const createCustomIcon = (incident: Incident) => {
    const isSelected = selectedIncident?.id === incident.id;
    const statusColor =
      incident.status === "OPEN"
        ? "bg-red-500"
        : incident.status === "IN_PROGRESS"
          ? "bg-yellow-500"
          : "bg-green-500";

    return L.divIcon({
      html: `
        <div class="flex items-center justify-center ${isSelected ? "w-16 h-16" : "w-12 h-12"} rounded-full border-2 transition-all ${
          isSelected
            ? "bg-white/30 border-white shadow-lg"
            : "bg-white/20 border-white/60"
        } text-2xl">
          ${INCIDENT_TYPE_ICONS[incident.incidentType]}
        </div>
        <div class="absolute -bottom-1 -right-1 w-5 h-5 rounded-full ${statusColor} border-2 border-white ${
          incident.status === "OPEN" ? "animate-pulse" : ""
        }"></div>
      `,
      className: "custom-incident-marker",
      iconSize: [50, 50],
      iconAnchor: [25, 50],
      popupAnchor: [0, -50],
    });
  };

  return (
    <>
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution="&copy; OpenStreetMap contributors"
      />
      {incidents.slice(0, 8).map((incident) => {
        const lat = parseFloat(incident.coordinates.x);
        const lng = parseFloat(incident.coordinates.y);

        return (
          <Marker
            key={incident.id}
            position={[lat, lng]}
            icon={createCustomIcon(incident)}
            ref={(el) => {
              if (el) {
                markerRefs.current[incident.id] = el as any;
              }
            }}
            eventHandlers={{
              click: () => {
                onSelectIncident(
                  selectedIncident?.id === incident.id ? null : incident,
                );
              },
            }}
          >
            <Popup>
              <div className="min-w-xs">
                <div className="flex items-start gap-2">
                  <span className="text-2xl">
                    {INCIDENT_TYPE_ICONS[incident.incidentType]}
                  </span>
                  <div className="flex-1">
                    <h4 className="font-bold text-sm text-gray-900">
                      {incident.incidentTitle}
                    </h4>
                    <p className="text-xs text-gray-600 mt-1">
                      {incident.incidentSummary}
                    </p>
                    <div className="mt-2 flex items-center gap-2">
                      <span
                        className={`text-xs font-medium px-2 py-1 rounded ${
                          incident.status === "OPEN"
                            ? "bg-red-100 text-red-700"
                            : incident.status === "IN_PROGRESS"
                              ? "bg-yellow-100 text-yellow-700"
                              : "bg-green-100 text-green-700"
                        }`}
                      >
                        {incident.status}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </Popup>
          </Marker>
        );
      })}
    </>
  );
}

export function MapView({
  incidents,
  selectedIncident,
  onSelectIncident,
}: MapViewProps) {
  // Default center (Warsaw area)
  const defaultCenter: [number, number] = [51.76, 19.46];

  return (
    <div className="h-full w-full rounded-xl border border-dark-700 overflow-hidden">
      {incidents.length === 0 ? (
        <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-dark-800 to-dark-900">
          <div className="text-center">
            <p className="text-dark-400 text-lg">No incidents to display</p>
            <p className="text-dark-500 text-sm mt-2">
              Create or load incidents to see them on the map
            </p>
          </div>
        </div>
      ) : (
        <MapContainer
          center={defaultCenter}
          zoom={13}
          style={{ height: "100%", width: "100%" }}
        >
          <MapContent
            incidents={incidents}
            selectedIncident={selectedIncident}
            onSelectIncident={onSelectIncident}
          />
        </MapContainer>
      )}
    </div>
  );
}
