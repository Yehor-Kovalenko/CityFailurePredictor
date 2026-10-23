import { MapContainer, TileLayer, Marker, useMapEvents } from "react-leaflet";
import L from "leaflet";
import { DEFAULT_MAP_CENTER } from "../constants";
import { LatLng } from "../utils/geo";

interface LocationPickerProps {
  value: LatLng | null;
  onChange: (point: LatLng) => void;
}

const pinIcon = L.divIcon({
  html: '<div class="text-3xl leading-none">📍</div>',
  className: "location-picker-pin",
  iconSize: [30, 30],
  iconAnchor: [15, 30],
});

function ClickToMove({ onChange }: Pick<LocationPickerProps, "onChange">) {
  useMapEvents({
    click: (e) => onChange({ lat: e.latlng.lat, lng: e.latlng.lng }),
  });
  return null;
}

export function LocationPicker({ value, onChange }: LocationPickerProps) {
  const center: [number, number] = value ? [value.lat, value.lng] : DEFAULT_MAP_CENTER;

  return (
    <div className="h-48 w-full overflow-hidden rounded-md border border-gray-300">
      <MapContainer center={center} zoom={15} style={{ height: "100%", width: "100%" }}>
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution="&copy; OpenStreetMap contributors"
        />
        <ClickToMove onChange={onChange} />
        {value && (
          <Marker
            position={[value.lat, value.lng]}
            icon={pinIcon}
            draggable
            eventHandlers={{
              dragend: (e) => {
                const { lat, lng } = (e.target as L.Marker).getLatLng();
                onChange({ lat, lng });
              },
            }}
          />
        )}
      </MapContainer>
    </div>
  );
}
