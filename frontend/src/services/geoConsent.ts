export type GeoConsent = "unknown" | "granted" | "denied";

export const GEO_CONSENT_STORAGE_KEY = "city-failure-predictor.geo-consent";
export const GEO_CONSENT_VERSION = 1;
export const GEO_CONSENT_MAX_AGE_MS = 180 * 24 * 60 * 60 * 1000;

type GeoDecision = Exclude<GeoConsent, "unknown">;

interface StoredGeoConsent {
    decision: GeoDecision;
    version: number;
    decidedAt: number;
}

function isValidConsent(value: unknown, now: number): value is StoredGeoConsent {
    const stored = value as Partial<StoredGeoConsent> | null;
    return (
        (stored?.decision === "granted" || stored?.decision === "denied") &&
        stored.version === GEO_CONSENT_VERSION &&
        typeof stored.decidedAt === "number" &&
        stored.decidedAt <= now &&
        now - stored.decidedAt < GEO_CONSENT_MAX_AGE_MS
    );
}

export function readGeoConsent(now: number = Date.now()): GeoConsent {
    const raw = localStorage.getItem(GEO_CONSENT_STORAGE_KEY);
    if (raw === null) {
        return "unknown";
    }
    try {
        const stored: unknown = JSON.parse(raw);
        if (isValidConsent(stored, now)) {
            return stored.decision;
        }
    } catch {
    }
    clearGeoConsent();
    return "unknown";
}

export function storeGeoConsent(decision: GeoDecision): void {
    const stored: StoredGeoConsent = {
        decision,
        version: GEO_CONSENT_VERSION,
        decidedAt: Date.now(),
    };
    localStorage.setItem(GEO_CONSENT_STORAGE_KEY, JSON.stringify(stored));
}

export function clearGeoConsent(): void {
    localStorage.removeItem(GEO_CONSENT_STORAGE_KEY);
}
