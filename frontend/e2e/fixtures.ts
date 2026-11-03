import type { Page } from "@playwright/test";

const AUTH_STORAGE_KEY = "city-failure-predictor.auth";

const now = Date.now();

export const INCIDENTS = [
  {
    id: "e2e-1",
    incidentTitle: "Water main burst with an intentionally very long title to stress wrapping",
    incidentSummary: "Major water main rupture causing flooding on a busy street",
    coordinates: { crs: "EPSG:4326", x: "51.7589", y: "19.4560" },
    incidentType: "WATER",
    status: "OPEN",
    timestamp: new Date(now).toISOString(),
    lastUpdated: new Date(now).toISOString(),
  },
  {
    id: "e2e-2",
    incidentTitle: "Power outage",
    incidentSummary: "Electrical outage affecting downtown district",
    coordinates: { crs: "EPSG:4326", x: "51.7700", y: "19.4400" },
    incidentType: "ELECTRICITY",
    status: "IN_PROGRESS",
    timestamp: new Date(now - 600_000).toISOString(),
    lastUpdated: new Date(now).toISOString(),
  },
];

export const NOTIFICATIONS = [
  {
    id: "n-1",
    alertId: "a-1",
    householdId: "household-with-a-long-identifier-0001",
    title: "Critical overload risk predicted",
    message: "Predicted consumption exceeds the safe threshold",
    severity: "CRITICAL",
    status: "UNREAD",
    riskScore: 0.93,
    predictedKwh: 41.27,
    targetTimestamp: new Date(now + 3_600_000).toISOString(),
    createdAt: new Date(now).toISOString(),
  },
];

export async function bootstrapApp(page: Page): Promise<void> {
  await page.addInitScript(
    ([key, session]) => window.localStorage.setItem(key, session),
    [
      AUTH_STORAGE_KEY,
      JSON.stringify({
        accessToken: "e2e-token",
        tokenType: "Bearer",
        expiresAt: Date.now() + 3_600_000,
        user: { email: "a.very.long.operator.address@city-failure-predictor.example", role: "ROLE_ADMIN" },
      }),
    ] as const,
  );

  await page.route(/\/incidents\/all(\?.*)?$/, (route) => route.fulfill({ json: INCIDENTS }));
  await page.route(/\/notifications$/, (route) => route.fulfill({ json: NOTIFICATIONS }));
  await page.route(/tile\.openstreetmap\.org|cdnjs\.cloudflare\.com/, (route) => route.abort());
}
