import { getOfflineDb } from "./db";
import type { OutboxEntry, OutboxOperation } from "./types";

export const LOCAL_ID_PREFIX = "local-";

export function isLocalId(id: string): boolean {
  return id.startsWith(LOCAL_ID_PREFIX);
}

export function newLocalId(): string {
  return `${LOCAL_ID_PREFIX}${crypto.randomUUID()}`;
}

const listeners = new Set<() => void>();
const channel =
  typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel("cfp-outbox");

function notifyLocal(): void {
  listeners.forEach((listener) => listener());
}

channel?.addEventListener("message", notifyLocal);

function changed(): void {
  notifyLocal();
  channel?.postMessage("changed");
}

export function onOutboxChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function targetIncident(operation: OutboxOperation): string | null {
  switch (operation.type) {
    case "createIncident":
      return operation.localId;
    case "updateIncidentStatus":
    case "deleteIncident":
      return operation.incidentId;
    default:
      return null;
  }
}

export async function enqueue(owner: string, operation: OutboxOperation): Promise<void> {
  const db = await getOfflineDb();
  const tx = db.transaction("outbox", "readwrite");
  const queued = await tx.store.index("byOwner").getAll(owner);
  const removals: number[] = [];
  let skip = false;

  for (const entry of queued) {
    const previous = entry.operation;
    if (operation.type === "updateIncidentStatus" || operation.type === "deleteIncident") {
      if (targetIncident(previous) !== operation.incidentId) continue;
      if (previous.type === "updateIncidentStatus") {
        removals.push(entry.seq!);
      } else if (previous.type === "createIncident" && operation.type === "deleteIncident") {
        removals.push(entry.seq!);
        skip = true;
      }
    } else if (
      operation.type === "markNotificationRead" &&
      previous.type === "markNotificationRead" &&
      previous.notificationId === operation.notificationId
    ) {
      skip = true;
    }
  }

  await Promise.all(removals.map((seq) => tx.store.delete(seq)));
  if (!skip) {
    await tx.store.add({ owner, createdAt: Date.now(), attempts: 0, operation });
  }
  await tx.done;
  changed();
}

/** The user's queued operations in replay order. */
export async function listOutbox(owner: string): Promise<OutboxEntry[]> {
  const db = await getOfflineDb();
  return db.getAllFromIndex("outbox", "byOwner", owner);
}

export async function countOutbox(owner: string): Promise<number> {
  const db = await getOfflineDb();
  return db.countFromIndex("outbox", "byOwner", owner);
}

export async function removeFromOutbox(seq: number): Promise<void> {
  const db = await getOfflineDb();
  await db.delete("outbox", seq);
  changed();
}

export async function recordFailedAttempt(entry: OutboxEntry, error: string): Promise<void> {
  const db = await getOfflineDb();
  await db.put("outbox", { ...entry, attempts: entry.attempts + 1, lastError: error });
}
