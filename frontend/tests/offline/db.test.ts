import { beforeEach, describe, expect, it } from "vitest";
import { getOfflineDb, MIGRATIONS, type Migration, openVersionedDb } from "@/offline/db";
import { freshIndexedDb, OWNER } from "./helpers";

describe("offline database", () => {
  beforeEach(freshIndexedDb);

  it("creates the outbox and snapshot stores", async () => {
    const db = await getOfflineDb();

    expect(db.version).toBe(MIGRATIONS.length);
    expect([...db.objectStoreNames].sort()).toEqual(["outbox", "snapshots"]);
  });

  it("keeps queued changes and cached data when a newer app version upgrades the schema", async () => {
    const current = await openVersionedDb("upgrade-test", MIGRATIONS);
    await current.add("outbox", {
      owner: OWNER,
      createdAt: 1,
      attempts: 0,
      operation: { type: "deleteIncident", incidentId: "incident-1" },
    });
    await current.put("snapshots", { key: `${OWNER}:incidents`, owner: OWNER, savedAt: 1, data: [] });
    current.close();

    const addIndex: Migration = (_db, tx) => {
      tx.objectStore("outbox").createIndex("byOwner_v2" as never, "owner");
    };
    const next = await openVersionedDb("upgrade-test", [...MIGRATIONS, addIndex]);

    expect(next.version).toBe(MIGRATIONS.length + 1);
    expect(await next.getAll("outbox")).toEqual([
      expect.objectContaining({ operation: { type: "deleteIncident", incidentId: "incident-1" } }),
    ]);
    expect(await next.get("snapshots", `${OWNER}:incidents`)).toBeDefined();
    next.close();
  });
});
