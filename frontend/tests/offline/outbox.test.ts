import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  countOutbox,
  enqueue,
  isLocalId,
  listOutbox,
  newLocalId,
  onOutboxChange,
} from "@/offline/outbox";
import type { CreateIncidentRequest } from "@/types";
import { freshIndexedDb, OWNER } from "./helpers";

const request: CreateIncidentRequest = {
  incidentTitle: "Broken pipe",
  incidentSummary: "Water on the street",
  coordinates: { crs: "EPSG:4326", x: "51.75", y: "19.45" },
  incidentType: "WATER",
};

const operations = async (owner = OWNER) =>
  (await listOutbox(owner)).map((entry) => entry.operation);

describe("outbox", () => {
  beforeEach(freshIndexedDb);

  it("keeps operations in the order they were made", async () => {
    await enqueue(OWNER, { type: "createIncident", localId: "local-1", request });
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "incident-1" });
    await enqueue(OWNER, { type: "markNotificationRead", notificationId: "n-1" });

    expect((await operations()).map((op) => op.type)).toEqual([
      "createIncident",
      "deleteIncident",
      "markNotificationRead",
    ]);
  });

  it("keeps each user's changes separate", async () => {
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "incident-1" });
    await enqueue("other@example.com", { type: "deleteIncident", incidentId: "incident-2" });

    expect(await countOutbox(OWNER)).toBe(1);
    expect(await countOutbox("other@example.com")).toBe(1);
  });

  it("replaces an older status change of the same incident", async () => {
    await enqueue(OWNER, { type: "updateIncidentStatus", incidentId: "incident-1", status: "IN_PROGRESS" });
    await enqueue(OWNER, { type: "updateIncidentStatus", incidentId: "incident-2", status: "RESOLVED" });
    await enqueue(OWNER, { type: "updateIncidentStatus", incidentId: "incident-1", status: "RESOLVED" });

    expect(await operations()).toEqual([
      { type: "updateIncidentStatus", incidentId: "incident-2", status: "RESOLVED" },
      { type: "updateIncidentStatus", incidentId: "incident-1", status: "RESOLVED" },
    ]);
  });

  it("drops queued status changes of an incident that is deleted", async () => {
    await enqueue(OWNER, { type: "updateIncidentStatus", incidentId: "incident-1", status: "RESOLVED" });
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "incident-1" });

    expect(await operations()).toEqual([{ type: "deleteIncident", incidentId: "incident-1" }]);
  });

  it("cancels the creation of an incident deleted before it was synchronized", async () => {
    const localId = newLocalId();
    await enqueue(OWNER, { type: "createIncident", localId, request });
    await enqueue(OWNER, { type: "deleteIncident", incidentId: localId });

    expect(await countOutbox(OWNER)).toBe(0);
  });

  it("does not queue the same notification twice", async () => {
    await enqueue(OWNER, { type: "markNotificationRead", notificationId: "n-1" });
    await enqueue(OWNER, { type: "markNotificationRead", notificationId: "n-1" });

    expect(await countOutbox(OWNER)).toBe(1);
  });

  it("notifies subscribers about changes", async () => {
    const listener = vi.fn();
    const unsubscribe = onOutboxChange(listener);

    await enqueue(OWNER, { type: "deleteIncident", incidentId: "incident-1" });
    unsubscribe();
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "incident-2" });

    expect(listener).toHaveBeenCalledOnce();
  });

  it("recognizes temporary ids", () => {
    expect(isLocalId(newLocalId())).toBe(true);
    expect(isLocalId("3f1c2a4e-0000-4000-8000-000000000000")).toBe(false);
  });
});
