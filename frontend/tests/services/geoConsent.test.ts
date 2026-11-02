import { afterEach, describe, expect, it, vi } from "vitest";
import {
  GEO_CONSENT_MAX_AGE_MS,
  GEO_CONSENT_STORAGE_KEY,
  GEO_CONSENT_VERSION,
  clearGeoConsent,
  readGeoConsent,
  storeGeoConsent,
} from "@/services/geoConsent";

const NOW = Date.UTC(2026, 9, 30);

function storeRaw(value: unknown) {
  localStorage.setItem(GEO_CONSENT_STORAGE_KEY, JSON.stringify(value));
}

describe("geoConsent", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("is unknown before the user decides", () => {
    expect(readGeoConsent()).toBe("unknown");
  });

  it("stores only the decision with its version and time", () => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);

    storeGeoConsent("granted");

    expect(JSON.parse(localStorage.getItem(GEO_CONSENT_STORAGE_KEY)!)).toEqual({
      decision: "granted",
      version: GEO_CONSENT_VERSION,
      decidedAt: NOW,
    });
    expect(readGeoConsent(NOW)).toBe("granted");
  });

  it("remembers a refusal", () => {
    storeGeoConsent("denied");

    expect(readGeoConsent()).toBe("denied");
  });

  it("asks again once the decision has expired", () => {
    storeRaw({ decision: "granted", version: GEO_CONSENT_VERSION, decidedAt: NOW - GEO_CONSENT_MAX_AGE_MS });

    expect(readGeoConsent(NOW)).toBe("unknown");
    expect(localStorage.getItem(GEO_CONSENT_STORAGE_KEY)).toBeNull();
  });

  it("asks again when the consent text changed", () => {
    storeRaw({ decision: "granted", version: GEO_CONSENT_VERSION - 1, decidedAt: NOW });

    expect(readGeoConsent(NOW)).toBe("unknown");
  });

  it("does not trust a decision dated in the future", () => {
    storeRaw({ decision: "granted", version: GEO_CONSENT_VERSION, decidedAt: NOW + 1000 });

    expect(readGeoConsent(NOW)).toBe("unknown");
  });

  it("ignores values from older app versions and corrupted entries", () => {
    localStorage.setItem(GEO_CONSENT_STORAGE_KEY, "granted");
    expect(readGeoConsent()).toBe("unknown");

    storeRaw({ decision: "maybe", version: GEO_CONSENT_VERSION, decidedAt: NOW });
    expect(readGeoConsent(NOW)).toBe("unknown");

    storeRaw(null);
    expect(readGeoConsent(NOW)).toBe("unknown");
  });

  it("forgets the decision on demand", () => {
    storeGeoConsent("granted");

    clearGeoConsent();

    expect(readGeoConsent()).toBe("unknown");
  });
});
