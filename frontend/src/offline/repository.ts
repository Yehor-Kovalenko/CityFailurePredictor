import { incidentService, notificationService } from "../services/api";
import { getStoredSession } from "../services/authStorage";
import {
  type CreateIncidentRequest,
  type Incident,
  type IncidentStatus,
  type IncidentType,
  type Notification,
  NotificationStatus,
} from "../types";
import { isBrowserOffline, isNetworkError } from "./network";
import { countOutbox, enqueue, isLocalId, listOutbox, newLocalId } from "./outbox";
import { loadSnapshot, saveSnapshot } from "./snapshots";
import { requestSync } from "./syncManager";
import type { OutboxEntry, OutboxOperation } from "./types";

const INCIDENTS_SNAPSHOT = "incidents";
const NOTIFICATIONS_SNAPSHOT = "notifications";

export interface LoadResult<T> {
  data: T;
  /** True when the server was unreachable and the last saved copy is shown. */
  fromCache: boolean;
  /** When the data was fetched from the server. */
  savedAt: number;
}

export interface MutationResult<T = void> {
  data: T;
  /** True when the change was stored locally and will be sent on the next sync. */
  queued: boolean;
}

function requireOwner(): string {
  const session = getStoredSession();
  if (!session) {
    throw new Error("You need to be signed in");
  }
  return session.user.email;
}

/** Network first; on a connection failure falls back to the snapshot saved by the last success. */
async function networkFirst<T>(
  owner: string,
  snapshotName: string | null,
  fetchRemote: () => Promise<T>,
  fallbackSnapshot: string,
  fromSnapshot: (data: T) => T = (data) => data,
): Promise<LoadResult<T>> {
  try {
    const data = await fetchRemote();
    const savedAt = Date.now();
    if (snapshotName) {
      await saveSnapshot(owner, snapshotName, data, savedAt);
    }
    return { data, fromCache: false, savedAt };
  } catch (error) {
    if (!isNetworkError(error)) throw error;
    const snapshot = await loadSnapshot<T>(owner, fallbackSnapshot);
    if (!snapshot) throw error;
    return { data: fromSnapshot(snapshot.data), fromCache: true, savedAt: snapshot.savedAt };
  }
}

/**
 * Sends a write now, or queues it when the browser is offline, the request fails for lack of a
 * connection, or older changes are still queued (so the server receives them in order).
 */
async function sendOrQueue<T>(
  operation: OutboxOperation,
  send: () => Promise<T>,
  optimistic: T,
): Promise<MutationResult<T>> {
  const owner = requireOwner();
  if (!isBrowserOffline() && (await countOutbox(owner)) === 0) {
    try {
      return { data: await send(), queued: false };
    } catch (error) {
      if (!isNetworkError(error)) throw error;
    }
  }
  await enqueue(owner, operation);
  void requestSync();
  return { data: optimistic, queued: true };
}

function matchesFilters(incident: Incident, status?: IncidentStatus, type?: IncidentType): boolean {
  return (!status || incident.status === status) && (!type || incident.incidentType === type);
}

/** Shows queued changes on top of server data, so the UI reflects what the user did offline. */
export function applyPendingIncidentChanges(
  incidents: Incident[],
  pending: OutboxEntry[],
): Incident[] {
  let result = [...incidents];
  for (const { operation, createdAt } of pending) {
    switch (operation.type) {
      case "createIncident": {
        const timestamp = new Date(createdAt).toISOString();
        result = [
          {
            ...operation.request,
            id: operation.localId,
            status: "OPEN",
            timestamp,
            lastUpdated: timestamp,
            pendingSync: true,
          },
          ...result,
        ];
        break;
      }
      case "updateIncidentStatus":
        result = result.map((incident) =>
          incident.id === operation.incidentId
            ? { ...incident, status: operation.status, pendingSync: true }
            : incident,
        );
        break;
      case "deleteIncident":
        result = result.filter((incident) => incident.id !== operation.incidentId);
        break;
    }
  }
  return result;
}

export function applyPendingNotificationChanges(
  notifications: Notification[],
  pending: OutboxEntry[],
): Notification[] {
  const read = new Set(
    pending.flatMap(({ operation }) =>
      operation.type === "markNotificationRead" ? [operation.notificationId] : [],
    ),
  );
  return notifications.map((notification) =>
    read.has(notification.id) ? { ...notification, status: NotificationStatus.READ } : notification,
  );
}

export const incidentRepository = {
  async getAll(status?: IncidentStatus, type?: IncidentType): Promise<LoadResult<Incident[]>> {
    const owner = requireOwner();
    const unfiltered = !status && !type;
    const result = await networkFirst(
      owner,
      unfiltered ? INCIDENTS_SNAPSHOT : null,
      () => incidentService.getAllIncidents(status, type),
      INCIDENTS_SNAPSHOT,
      // The snapshot holds the full list; filter it the way the server would.
      (all) => all.filter((incident) => matchesFilters(incident, status, type)),
    );
    const pending = await listOutbox(owner);
    return {
      ...result,
      data: applyPendingIncidentChanges(result.data, pending).filter((incident) =>
        matchesFilters(incident, status, type),
      ),
    };
  },

  create(request: CreateIncidentRequest): Promise<MutationResult<Incident>> {
    const localId = newLocalId();
    const now = new Date().toISOString();
    return sendOrQueue(
      { type: "createIncident", localId, request },
      () => incidentService.createIncident(request),
      { ...request, id: localId, status: "OPEN", timestamp: now, lastUpdated: now, pendingSync: true },
    );
  },

  updateStatus(incident: Incident, status: IncidentStatus): Promise<MutationResult<Incident>> {
    if (isLocalId(incident.id)) {
      // The server does not know this incident yet; it can change status once it is synced.
      return Promise.reject(new Error("Wait until the incident is synchronized"));
    }
    return sendOrQueue(
      { type: "updateIncidentStatus", incidentId: incident.id, status },
      () => incidentService.updateIncidentStatus(incident.id, { status }),
      { ...incident, status, pendingSync: true },
    );
  },

  async remove(id: string): Promise<MutationResult> {
    if (isLocalId(id)) {
      // Never reached the server: cancelling the queued creation is enough.
      await enqueue(requireOwner(), { type: "deleteIncident", incidentId: id });
      return { data: undefined, queued: false };
    }
    return sendOrQueue(
      { type: "deleteIncident", incidentId: id },
      () => incidentService.deleteIncident(id),
      undefined,
    );
  },
};

export const notificationRepository = {
  async getAll(): Promise<LoadResult<Notification[]>> {
    const owner = requireOwner();
    const result = await networkFirst(
      owner,
      NOTIFICATIONS_SNAPSHOT,
      () => notificationService.getNotifications(),
      NOTIFICATIONS_SNAPSHOT,
    );
    return { ...result, data: applyPendingNotificationChanges(result.data, await listOutbox(owner)) };
  },

  async markAsRead(id: string): Promise<MutationResult> {
    return sendOrQueue(
      { type: "markNotificationRead", notificationId: id },
      async () => {
        await notificationService.markNotificationAsRead(id);
      },
      undefined,
    );
  },
};
