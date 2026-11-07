import { expect, test as base, type Page } from "@playwright/test";
import {
  bootstrapApp,
  cutApi,
  INCIDENTS,
  restoreApi,
  waitForServiceWorker,
} from "./fixtures";
import { type ReleaseServer, startReleaseServer } from "./releaseServer";

const test = base.extend<{ releaseServer: ReleaseServer }>({
  releaseServer: async ({}, provide) => {
    const server = await startReleaseServer();
    await provide(server);
    await server.close();
  },
});

const NEW_INCIDENT = {
  incidentTitle: "Gas leak reported offline",
  incidentSummary: "Smell of gas near the school",
  coordinates: { crs: "EPSG:4326", x: "51.7600", y: "19.4500" },
  incidentType: "FIRE",
};

async function openIncidents(page: Page) {
  await page.getByRole("button", { name: "Incidents", exact: true }).click();
}

async function createIncident(page: Page) {
  await page.getByRole("button", { name: "New Incident", exact: true }).click();
  const dialog = page.getByRole("dialog", { name: "Create New Incident" });
  await dialog.getByPlaceholder("e.g., Water Main Burst").fill(NEW_INCIDENT.incidentTitle);
  await dialog.getByPlaceholder("Describe the incident...").fill(NEW_INCIDENT.incidentSummary);
  await dialog.getByPlaceholder("51.5074").fill(NEW_INCIDENT.coordinates.x);
  await dialog.getByPlaceholder("19.4568").fill(NEW_INCIDENT.coordinates.y);
  await dialog.getByRole("button", { name: "Create Incident" }).click();
  await expect(dialog).toBeHidden();
}

async function acceptCreatedIncidents(page: Page) {
  const received: unknown[] = [];
  await page.route(/\/incidents$/, async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const body = route.request().postDataJSON();
    received.push(body);
    await route.fulfill({
      status: 201,
      json: { ...body, id: "server-1", status: "OPEN", timestamp: new Date().toISOString(), lastUpdated: new Date().toISOString() },
    });
  });
  return received;
}

const pendingCount = (page: Page) => page.getByTestId("pending-count");

test.describe("offline mode", () => {
  test("starts without a connection and shows the data saved on the last visit", async ({ page, context }) => {
    await bootstrapApp(page);
    await page.goto("/");
    await waitForServiceWorker(page);
    await openIncidents(page);
    await expect(page.getByRole("heading", { name: INCIDENTS[1].incidentTitle })).toBeVisible();

    await context.setOffline(true);
    await cutApi(page);
    await page.reload();

    await expect(page.getByText("You are offline.")).toBeVisible();
    await openIncidents(page);
    await expect(page.getByRole("heading", { name: INCIDENTS[1].incidentTitle })).toBeVisible();
    await expect(page.getByText("Error loading incidents")).toBeHidden();
  });

  test("queues changes made offline and synchronizes them when the connection returns", async ({ page, context }) => {
    await bootstrapApp(page);
    const received = await acceptCreatedIncidents(page);
    await page.goto("/");
    await openIncidents(page);
    await expect(page.getByRole("heading", { name: INCIDENTS[0].incidentTitle })).toBeVisible();

    await context.setOffline(true);
    await cutApi(page);
    await createIncident(page);
    page.once("dialog", (dialog) => void dialog.accept());
    await page
      .locator("div", { has: page.getByRole("heading", { name: INCIDENTS[1].incidentTitle }) })
      .getByRole("button", { name: "Delete incident" })
      .last()
      .click();

    await expect(pendingCount(page)).toHaveText("2");
    await expect(page.getByRole("heading", { name: NEW_INCIDENT.incidentTitle })).toBeVisible();
    await expect(page.getByText("Pending sync")).toBeVisible();
    await expect(page.getByRole("heading", { name: INCIDENTS[1].incidentTitle })).toBeHidden();
    expect(received).toEqual([]);

    const deleted = page.waitForRequest(
      (request) => request.method() === "DELETE" && request.url().endsWith(`/incidents/${INCIDENTS[1].id}`),
    );
    await page.route(/\/incidents\/[^/]+$/, (route) =>
      route.request().method() === "DELETE" ? route.fulfill({ status: 204 }) : route.fallback(),
    );
    await restoreApi(page);
    await context.setOffline(false);

    await deleted;
    await expect(pendingCount(page)).toBeHidden();
    expect(received).toEqual([NEW_INCIDENT]);
  });
});

test.describe("app update", () => {
  test("keeps unsynchronized changes and cached data when a new version is installed", async ({ page, releaseServer }) => {
    await bootstrapApp(page);
    await page.goto(releaseServer.url);
    await waitForServiceWorker(page);
    await openIncidents(page);
    await expect(page.getByRole("heading", { name: INCIDENTS[0].incidentTitle })).toBeVisible();

    await cutApi(page);
    await createIncident(page);
    await expect(pendingCount(page)).toHaveText("1");

    releaseServer.deployNewRelease();
    await page.evaluate(async () => (await navigator.serviceWorker.getRegistration())?.update());

    const prompt = page.getByRole("alertdialog", { name: "A new version is available" });
    await expect(prompt).toContainText("Your 1 unsynced change is kept on this device");
    await Promise.all([page.waitForEvent("load"), prompt.getByRole("button", { name: "Reload" }).click()]);

    await expect
      .poll(() => page.evaluate(async () => Boolean((await navigator.serviceWorker.getRegistration())?.waiting)))
      .toBe(false);
    await expect(prompt).toBeHidden();
    await expect(pendingCount(page)).toHaveText("1");
    await expect(page.getByText("Server unreachable.")).toBeVisible();
    await openIncidents(page);
    await expect(page.getByRole("heading", { name: NEW_INCIDENT.incidentTitle })).toBeVisible();
    await expect(page.getByRole("heading", { name: INCIDENTS[0].incidentTitle })).toBeVisible();

    const received = await acceptCreatedIncidents(page);
    await restoreApi(page);
    await page.getByRole("button", { name: /Sync now/ }).click();
    await expect(pendingCount(page)).toBeHidden();
    expect(received).toEqual([NEW_INCIDENT]);
  });
});
