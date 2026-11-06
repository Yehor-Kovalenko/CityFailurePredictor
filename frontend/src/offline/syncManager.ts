import { getAuthChangedEventName, getStoredSession } from "../services/authStorage";
import { publish } from "../utils/eventBroker";
import { EventFeedItemLevel } from "../types";
import { countOutbox, onOutboxChange } from "./outbox";
import { syncOutbox, type SyncReport } from "./sync";

export const OUTBOX_SYNCED_EVENT = "outbox_synced";
export const SYNC_RETRY_INTERVAL_MS = 30_000;
const SYNC_LOCK_NAME = "city-failure-predictor-outbox-sync";

export interface SyncState {
  online: boolean;
  pending: number;
  syncing: boolean;
  lastSyncedAt: number | null;
  lastError: string | null;
}

let state: SyncState = {
  online: typeof navigator === "undefined" ? true : navigator.onLine,
  pending: 0,
  syncing: false,
  lastSyncedAt: null,
  lastError: null,
};
const subscribers = new Set<() => void>();

function setState(patch: Partial<SyncState>): void {
  state = { ...state, ...patch };
  subscribers.forEach((subscriber) => subscriber());
}

export function getSyncState(): SyncState {
  return state;
}

export function subscribeSyncState(subscriber: () => void): () => void {
  subscribers.add(subscriber);
  return () => subscribers.delete(subscriber);
}

function currentOwner(): string | null {
  return getStoredSession()?.user.email ?? null;
}

export async function refreshPendingCount(): Promise<void> {
  const owner = currentOwner();
  setState({ pending: owner ? await countOutbox(owner) : 0 });
}

function reportResult(report: SyncReport): void {
  if (report.synced > 0) {
    publish("event_feed", {
      title: `Synchronized ${report.synced} offline change${report.synced === 1 ? "" : "s"}`,
      eventLevel: EventFeedItemLevel.INFO,
      type: "Sync",
    });
  }
  for (const { entry, reason } of report.rejected) {
    publish("event_feed", {
      title: "Offline change rejected by the server",
      summary: `${entry.operation.type}: ${reason}`,
      eventLevel: EventFeedItemLevel.ERROR,
      type: "Sync",
    });
  }
  if (report.synced > 0 || report.rejected.length > 0) {
    publish(OUTBOX_SYNCED_EVENT, report);
  }
}

function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== "undefined" && navigator.locks) {
    return navigator.locks.request(SYNC_LOCK_NAME, task) as Promise<T>;
  }
  return task();
}

let running: Promise<SyncReport | null> | null = null;

export function requestSync(): Promise<SyncReport | null> {
  running ??= (async () => {
    const owner = currentOwner();
    if (!owner || !state.online) {
      return null;
    }
    setState({ syncing: true });
    try {
      const report = await withCrossTabLock(() => syncOutbox(owner));
      reportResult(report);
      setState({
        lastSyncedAt: report.stoppedBy ? state.lastSyncedAt : Date.now(),
        lastError:
          report.stoppedBy === "auth"
            ? "Sign in again to synchronize your changes"
            : report.stoppedBy === "retry"
              ? "Server unreachable, will retry"
              : null,
      });
      return report;
    } catch (error) {
      setState({ lastError: error instanceof Error ? error.message : "Sync failed" });
      return null;
    } finally {
      await refreshPendingCount();
      setState({ syncing: false });
    }
  })().finally(() => {
    running = null;
  });
  return running;
}

export function startSyncManager(): () => void {
  const syncIfPending = () => {
    void refreshPendingCount().then(() => {
      if (state.pending > 0) void requestSync();
    });
  };
  const handleOnline = () => {
    setState({ online: true });
    syncIfPending();
  };
  const handleOffline = () => setState({ online: false });
  const handleVisibility = () => {
    if (document.visibilityState === "visible") syncIfPending();
  };
  const authEvent = getAuthChangedEventName();

  window.addEventListener("online", handleOnline);
  window.addEventListener("offline", handleOffline);
  window.addEventListener(authEvent, syncIfPending);
  document.addEventListener("visibilitychange", handleVisibility);
  const stopOutbox = onOutboxChange(() => {
    if (!state.syncing) syncIfPending();
  });
  const timer = window.setInterval(() => {
    if (state.pending > 0 && state.online) void requestSync();
  }, SYNC_RETRY_INTERVAL_MS);

  setState({ online: navigator.onLine });
  syncIfPending();

  return () => {
    window.removeEventListener("online", handleOnline);
    window.removeEventListener("offline", handleOffline);
    window.removeEventListener(authEvent, syncIfPending);
    document.removeEventListener("visibilitychange", handleVisibility);
    stopOutbox();
    window.clearInterval(timer);
  };
}

export function resetSyncStateForTests(): void {
  running = null;
  state = { online: true, pending: 0, syncing: false, lastSyncedAt: null, lastError: null };
}
