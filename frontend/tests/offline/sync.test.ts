import { beforeEach, describe, expect, it, vi } from "vitest";
import { enqueue, listOutbox } from "@/offline/outbox";
import { syncOutbox } from "@/offline/sync";
import type { OutboxOperation } from "@/offline/types";
import { freshIndexedDb, httpError, networkError, OWNER } from "./helpers";

const del = (incidentId: string): OutboxOperation => ({ type: "deleteIncident", incidentId });
const read = (notificationId: string): OutboxOperation => ({ type: "markNotificationRead", notificationId });

async function queue(...operations: OutboxOperation[]) {
  for (const operation of operations) await enqueue(OWNER, operation);
}

describe("syncOutbox", () => {
  beforeEach(freshIndexedDb);

  it("replays every operation in order and empties the outbox", async () => {
    await queue(del("a"), read("n-1"), del("b"));
    const replay = vi.fn().mockResolvedValue(undefined);

    const report = await syncOutbox(OWNER, replay);

    expect(replay.mock.calls.map(([op]) => op)).toEqual([del("a"), read("n-1"), del("b")]);
    expect(report).toEqual({ synced: 3, rejected: [], remaining: 0 });
    expect(await listOutbox(OWNER)).toEqual([]);
  });

  it("stops at a connection failure and keeps the rest in order for the next attempt", async () => {
    await queue(del("a"), del("b"), del("c"));
    const replay = vi.fn().mockResolvedValueOnce(undefined).mockRejectedValueOnce(networkError());

    const report = await syncOutbox(OWNER, replay);

    expect(replay).toHaveBeenCalledTimes(2);
    expect(report).toMatchObject({ synced: 1, remaining: 2, stoppedBy: "retry" });
    const left = await listOutbox(OWNER);
    expect(left.map((entry) => entry.operation)).toEqual([del("b"), del("c")]);
    expect(left[0]).toMatchObject({ attempts: 1, lastError: "Network Error" });
  });

  it("retries server errors and rate limiting later", async () => {
    await queue(del("a"));

    for (const status of [500, 503, 429, 408]) {
      const report = await syncOutbox(OWNER, vi.fn().mockRejectedValue(httpError(status)));
      expect(report.stoppedBy).toBe("retry");
    }
    expect((await listOutbox(OWNER))[0].attempts).toBe(4);
  });

  it("keeps changes until the user signs in again when the session expired", async () => {
    await queue(del("a"));

    const report = await syncOutbox(OWNER, vi.fn().mockRejectedValue(httpError(401)));

    expect(report.stoppedBy).toBe("auth");
    expect(await listOutbox(OWNER)).toHaveLength(1);
  });

  it("drops and reports a change the server rejects, then continues", async () => {
    await queue(read("n-1"), del("b"));
    const replay = vi
      .fn()
      .mockRejectedValueOnce(httpError(400, "Notification cannot be changed"))
      .mockResolvedValueOnce(undefined);

    const report = await syncOutbox(OWNER, replay);

    expect(report.synced).toBe(1);
    expect(report.rejected).toEqual([
      { entry: expect.objectContaining({ operation: read("n-1") }), reason: "Notification cannot be changed" },
    ]);
    expect(await listOutbox(OWNER)).toEqual([]);
  });

  it("treats deleting an incident that no longer exists as done", async () => {
    await queue(del("gone"));

    const report = await syncOutbox(OWNER, vi.fn().mockRejectedValue(httpError(404)));

    expect(report).toMatchObject({ synced: 1, rejected: [] });
  });

  it("only replays the signed-in user's changes", async () => {
    await enqueue("other@example.com", del("theirs"));
    const replay = vi.fn().mockResolvedValue(undefined);

    await syncOutbox(OWNER, replay);

    expect(replay).not.toHaveBeenCalled();
    expect(await listOutbox("other@example.com")).toHaveLength(1);
  });
});
