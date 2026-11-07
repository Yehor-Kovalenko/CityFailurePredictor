import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { enqueue, listOutbox } from "@/offline/outbox";
import {
  getSyncState,
  OUTBOX_SYNCED_EVENT,
  requestSync,
  resetSyncStateForTests,
  startSyncManager,
} from "@/offline/syncManager";
import { EventFeedItem, EventFeedItemLevel } from "@/types";
import { freshIndexedDb, httpError, OWNER, signIn } from "./helpers";

const { replay, publish } = vi.hoisted(() => ({ replay: vi.fn(), publish: vi.fn() }));

vi.mock("@/offline/sync", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/offline/sync")>();
  return { ...actual, syncOutbox: (owner: string) => actual.syncOutbox(owner, replay) };
});
vi.mock("@/utils/eventBroker", () => ({ publish }));

const feedEvents = () =>
  publish.mock.calls.filter(([event]) => event === "event_feed").map(([, item]) => item as EventFeedItem);

describe("sync manager", () => {
  let stop: (() => void) | undefined;

  beforeEach(async () => {
    await freshIndexedDb();
    resetSyncStateForTests();
    signIn();
  });

  afterEach(() => {
    stop?.();
    stop = undefined;
    vi.resetAllMocks();
    vi.restoreAllMocks();
  });

  it("synchronizes queued changes and announces the result", async () => {
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "1" });
    replay.mockResolvedValue(undefined);

    const report = await requestSync();

    expect(report?.synced).toBe(1);
    expect(getSyncState()).toMatchObject({ pending: 0, syncing: false, lastError: null });
    expect(getSyncState().lastSyncedAt).not.toBeNull();
    expect(feedEvents()).toEqual([expect.objectContaining({ title: "Synchronized 1 offline change" })]);
    expect(publish).toHaveBeenCalledWith(OUTBOX_SYNCED_EVENT, report);
  });

  it("reports changes the server rejected", async () => {
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "1" });
    replay.mockRejectedValue(httpError(409, "Incident is locked"));

    await requestSync();

    expect(feedEvents()).toEqual([
      expect.objectContaining({
        title: "Offline change rejected by the server",
        eventLevel: EventFeedItemLevel.ERROR,
      }),
    ]);
  });

  it("asks the user to sign in when the session expired", async () => {
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "1" });
    replay.mockRejectedValue(httpError(401));

    await requestSync();

    expect(getSyncState()).toMatchObject({
      pending: 1,
      lastError: "Sign in again to synchronize your changes",
    });
  });

  it("shares one run between concurrent requests", async () => {
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "1" });
    replay.mockResolvedValue(undefined);

    const [first, second] = await Promise.all([requestSync(), requestSync()]);

    expect(first).toBe(second);
    expect(replay).toHaveBeenCalledOnce();
  });

  it("waits while offline and synchronizes as soon as the connection returns", async () => {
    vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
    replay.mockResolvedValue(undefined);
    stop = startSyncManager();
    await enqueue(OWNER, { type: "deleteIncident", incidentId: "1" });

    await vi.waitFor(() => expect(getSyncState()).toMatchObject({ online: false, pending: 1 }));
    expect(replay).not.toHaveBeenCalled();

    window.dispatchEvent(new Event("online"));

    await vi.waitFor(async () => expect(await listOutbox(OWNER)).toEqual([]));
    expect(getSyncState()).toMatchObject({ online: true, pending: 0 });
  });

  it("does nothing without a signed-in user", async () => {
    localStorage.clear();

    expect(await requestSync()).toBeNull();
  });
});
