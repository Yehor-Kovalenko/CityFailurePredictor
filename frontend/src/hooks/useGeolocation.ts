import { useCallback, useEffect, useState } from "react";
import {
  LatLng,
  geolocationErrorMessage,
  isGeolocationSupported,
  isPermissionDenied,
} from "../utils/geo";

export type GeoConsent = "unknown" | "granted" | "denied";

const GEO_CONSENT_STORAGE_KEY = "city-failure-predictor.geo-consent";

export function readGeoConsent(): GeoConsent {
  const stored = localStorage.getItem(GEO_CONSENT_STORAGE_KEY);
  return stored === "granted" || stored === "denied" ? stored : "unknown";
}

function storeGeoConsent(consent: Exclude<GeoConsent, "unknown">): void {
  localStorage.setItem(GEO_CONSENT_STORAGE_KEY, consent);
}

export function useGeolocation() {
  const [consent, setConsent] = useState<GeoConsent>(readGeoConsent);
  const [position, setPosition] = useState<LatLng | null>(null);
  const [error, setError] = useState<string | null>(null);
  const supported = isGeolocationSupported();

  useEffect(() => {
    if (consent !== "granted" || !supported) {
      return;
    }

    const geolocation = navigator.geolocation;
    const watchId = geolocation.watchPosition(
      (pos) => {
        setPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setError(null);
      },
      (err) => {
        if (isPermissionDenied(err)) {
          storeGeoConsent("denied");
          setConsent("denied");
          setPosition(null);
        }
        setError(geolocationErrorMessage(err));
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 30000 },
    );

    return () => geolocation.clearWatch(watchId);
  }, [consent, supported]);

  const grant = useCallback(() => {
    storeGeoConsent("granted");
    setError(null);
    setConsent("granted");
  }, []);

  const deny = useCallback(() => {
    storeGeoConsent("denied");
    setPosition(null);
    setConsent("denied");
  }, []);

  return {
    consent,
    position: consent === "granted" ? position : null,
    error: supported ? error : "Geolocation is not supported by this browser",
    grant,
    deny,
  };
}
