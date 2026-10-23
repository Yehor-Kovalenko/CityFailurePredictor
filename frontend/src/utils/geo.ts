import { Incident } from "../types";

export interface LatLng {
  lat: number;
  lng: number;
}

const EARTH_RADIUS_KM = 6371;
const PERMISSION_DENIED = 1;

const toRadians = (degrees: number) => (degrees * Math.PI) / 180;

export function distanceKm(a: LatLng, b: LatLng): number {
  const dLat = toRadians(b.lat - a.lat);
  const dLng = toRadians(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
}

export function incidentPosition(incident: Incident): LatLng {
  return {
    lat: parseFloat(incident.coordinates.x),
    lng: parseFloat(incident.coordinates.y),
  };
}

export function filterWithinRadius(
  incidents: Incident[],
  center: LatLng,
  radiusKm: number,
): Incident[] {
  return incidents.filter(
    (incident) => distanceKm(center, incidentPosition(incident)) <= radiusKm,
  );
}

export function isGeolocationSupported(): boolean {
  return typeof navigator !== "undefined" && "geolocation" in navigator;
}

export function isPermissionDenied(error: GeolocationPositionError): boolean {
  return error.code === PERMISSION_DENIED;
}

export function geolocationErrorMessage(error: GeolocationPositionError): string {
  if (isPermissionDenied(error)) {
    return "Location access was denied";
  }
  return "Unable to determine your location";
}

export function getCurrentPosition(): Promise<LatLng> {
  return new Promise((resolve, reject) => {
    if (!isGeolocationSupported()) {
      reject(new Error("Geolocation is not supported by this browser"));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) =>
        resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
      (error) => reject(new Error(geolocationErrorMessage(error))),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 },
    );
  });
}
