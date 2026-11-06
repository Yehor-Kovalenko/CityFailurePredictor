import { getOfflineDb } from "./db";
import type { Snapshot } from "./types";

const keyOf = (owner: string, name: string) => `${owner}:${name}`;

/** Stores the last data fetched from the server, so it can be shown offline. */
export async function saveSnapshot<T>(
  owner: string,
  name: string,
  data: T,
  savedAt = Date.now(),
): Promise<void> {
  const db = await getOfflineDb();
  await db.put("snapshots", { key: keyOf(owner, name), owner, savedAt, data });
}

export async function loadSnapshot<T>(owner: string, name: string): Promise<Snapshot<T> | null> {
  const db = await getOfflineDb();
  return ((await db.get("snapshots", keyOf(owner, name))) as Snapshot<T> | undefined) ?? null;
}

/** Removes a user's cached server data (on logout). The outbox is deliberately kept. */
export async function clearSnapshots(owner: string): Promise<void> {
  const db = await getOfflineDb();
  const tx = db.transaction("snapshots", "readwrite");
  const keys = await tx.store.index("byOwner").getAllKeys(owner);
  await Promise.all([...keys.map((key) => tx.store.delete(key)), tx.done]);
}
