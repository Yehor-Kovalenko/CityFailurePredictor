import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { incidentRepository, notificationRepository } from "@/offline/repository";
import { countOutbox, enqueue, listOutbox } from "@/offline/outbox";
import { clearSnapshots } from "@/offline/snapshots";
import {
  type CreateIncidentRequest,
  type Incident,
  type Notification,
  NotificationSeverity,
  NotificationStatus,
} from "@/types";
import { freshIndexedDb, httpError, networkError, OWNER, signIn } from "./helpers";

const api = vi.hoisted(() => ({
  getAllIncidents: vi.fn(),
  createIncident: vi.fn(),
  updateIncidentStatus: vi.fn(),
  deleteIncident: vi.fn(),
  getNotifications: vi.fn(),
  markNotificationAsRead: vi.fn(),
}));

vi.mock("@/services/api", () => ({
  incidentService: {
    getAllIncidents: api.getAllIncidents,
    createIncident: api.createIncident,
    updateIncidentStatus: api.updateIncidentStatus,
    deleteIncident: api.deleteIncident,
  },
  notificationService: {
    getNotifications: api.getNotifications,
    markNotificationAsRead: api.markNotificationAsRead,
  },
}));

vi.mock("@/offline/syncManager", () => ({ requestSync: vi.fn() }));

function incident(id: string, overrides: Partial<Incident> = {}): Incident {
  return {
    id,
    incidentTitle: `Incident ${id}`,
    incidentSummary: "Summary",
    coordinates: { crs: "EPSG:4326", x: "51.75", y: "19.45" },
    incidentType: "WATER",
    status: "OPEN",
    timestamp: "2026-01-15T10:00:00Z",
    lastUpdated: "2026-01-15T10:00:00Z",
    ...overrides,
  };
}

const request: CreateIncidentRequest = {
  incidentTitle: "Broken pipe",
  incidentSummary: "Water on the street",
  coordinates: { crs: "EPSG:4326", x: "51.75", y: "19.45" },
  incidentType: "WATER",
};

const notification: Notification = {
  id: "n-1",
  alertId: "a-1",
  householdId: "h-1",
  title: "Overload risk",
  message: "Consumption above threshold",
  severity: NotificationSeverity.HIGH,
  status: NotificationStatus.UNREAD,
  riskScore: 0.8,
  predictedKwh: 12,
  targetTimestamp: "2026-01-15T12:00:00Z",
  createdAt: "2026-01-15T10:00:00Z",
};

function goOffline() {
  vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
}

describe("incidentRepository", () => {
  beforeEach(async () => {
    await freshIndexedDb();
    signIn();
  });

  afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
  });

  describe("getAll", () => {
    it("returns server data and keeps a copy for offline use", async () => {
      api.getAllIncidents.mockResolvedValueOnce([incident("1")]);

      const online = await incidentRepository.getAll();
      api.getAllIncidents.mockRejectedValueOnce(networkError());
      const offline = await incidentRepository.getAll();

      expect(online).toMatchObject({ data: [incident("1")], fromCache: false });
      expect(offline).toMatchObject({ data: [incident("1")], fromCache: true, savedAt: online.savedAt });
    });

    it("applies filters to the saved copy when offline", async () => {
      api.getAllIncidents.mockResolvedValueOnce([
        incident("1"),
        incident("2", { status: "RESOLVED" }),
        incident("3", { incidentType: "FIRE" }),
      ]);
      await incidentRepository.getAll();
      api.getAllIncidents.mockRejectedValueOnce(networkError());

      const result = await incidentRepository.getAll("OPEN", "WATER");

      expect(result.data.map((i) => i.id)).toEqual(["1"]);
    });

    it("does not overwrite the full saved copy with a filtered response", async () => {
      api.getAllIncidents.mockResolvedValueOnce([incident("1"), incident("2", { status: "RESOLVED" })]);
      await incidentRepository.getAll();
      api.getAllIncidents.mockResolvedValueOnce([incident("2", { status: "RESOLVED" })]);
      await incidentRepository.getAll("RESOLVED");
      api.getAllIncidents.mockRejectedValueOnce(networkError());

      expect((await incidentRepository.getAll()).data).toHaveLength(2);
    });

    it("fails when offline and nothing was saved yet", async () => {
      api.getAllIncidents.mockRejectedValueOnce(networkError());

      await expect(incidentRepository.getAll()).rejects.toThrow("Network Error");
    });

    it("propagates errors returned by the server", async () => {
      api.getAllIncidents.mockResolvedValueOnce([incident("1")]);
      await incidentRepository.getAll();
      api.getAllIncidents.mockRejectedValueOnce(httpError(500));

      await expect(incidentRepository.getAll()).rejects.toThrow("HTTP 500");
    });

    it("never shows another user's saved data", async () => {
      api.getAllIncidents.mockResolvedValueOnce([incident("1")]);
      await incidentRepository.getAll();
      signIn("other@example.com");
      api.getAllIncidents.mockRejectedValueOnce(networkError());

      await expect(incidentRepository.getAll()).rejects.toThrow("Network Error");
    });

    it("shows queued changes on top of server data", async () => {
      await enqueue(OWNER, { type: "createIncident", localId: "local-1", request });
      await enqueue(OWNER, { type: "updateIncidentStatus", incidentId: "1", status: "RESOLVED" });
      await enqueue(OWNER, { type: "deleteIncident", incidentId: "2" });
      api.getAllIncidents.mockResolvedValueOnce([incident("1"), incident("2"), incident("3")]);

      const { data } = await incidentRepository.getAll();

      expect(data.map(({ id, status, pendingSync }) => ({ id, status, pendingSync }))).toEqual([
        { id: "local-1", status: "OPEN", pendingSync: true },
        { id: "1", status: "RESOLVED", pendingSync: true },
        { id: "3", status: "OPEN", pendingSync: undefined },
      ]);
    });
  });

  describe("writes", () => {
    it("sends directly when online", async () => {
      api.createIncident.mockResolvedValueOnce(incident("server-1"));

      const result = await incidentRepository.create(request);

      expect(result).toEqual({ data: incident("server-1"), queued: false });
      expect(await countOutbox(OWNER)).toBe(0);
    });

    it("queues a new incident when the connection drops and shows it as pending", async () => {
      api.createIncident.mockRejectedValueOnce(networkError());

      const result = await incidentRepository.create(request);

      expect(result.queued).toBe(true);
      expect(result.data).toMatchObject({ ...request, status: "OPEN", pendingSync: true });
      expect(result.data.id).toMatch(/^local-/);
      expect(await listOutbox(OWNER)).toEqual([
        expect.objectContaining({
          operation: { type: "createIncident", localId: result.data.id, request },
        }),
      ]);
    });

    it("does not try the network when the browser is offline", async () => {
      goOffline();

      const result = await incidentRepository.updateStatus(incident("1"), "RESOLVED");

      expect(api.updateIncidentStatus).not.toHaveBeenCalled();
      expect(result).toEqual({ data: { ...incident("1"), status: "RESOLVED", pendingSync: true }, queued: true });
    });

    it("queues behind older changes so the server receives them in order", async () => {
      await enqueue(OWNER, { type: "updateIncidentStatus", incidentId: "1", status: "IN_PROGRESS" });

      await incidentRepository.remove("2");

      expect(api.deleteIncident).not.toHaveBeenCalled();
      expect((await listOutbox(OWNER)).map((entry) => entry.operation.type)).toEqual([
        "updateIncidentStatus",
        "deleteIncident",
      ]);
    });

    it("propagates validation errors instead of queueing them", async () => {
      api.createIncident.mockRejectedValueOnce(httpError(400, "Title is required"));

      await expect(incidentRepository.create(request)).rejects.toThrow("Title is required");
      expect(await countOutbox(OWNER)).toBe(0);
    });

    it("cancels the queued creation when an offline incident is deleted", async () => {
      goOffline();
      const { data } = await incidentRepository.create(request);

      const result = await incidentRepository.remove(data.id);

      expect(result.queued).toBe(false);
      expect(await countOutbox(OWNER)).toBe(0);
      expect(api.deleteIncident).not.toHaveBeenCalled();
    });

    it("refuses status changes of an incident the server does not know yet", async () => {
      await expect(
        incidentRepository.updateStatus(incident("local-1"), "RESOLVED"),
      ).rejects.toThrow("Wait until the incident is synchronized");
    });

    it("requires a signed-in user", async () => {
      localStorage.clear();

      await expect(incidentRepository.create(request)).rejects.toThrow("You need to be signed in");
    });
  });
});

describe("notificationRepository", () => {
  beforeEach(async () => {
    await freshIndexedDb();
    signIn();
  });

  afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
  });

  it("serves saved notifications offline with queued read markers applied", async () => {
    api.getNotifications.mockResolvedValueOnce([notification]);
    await notificationRepository.getAll();
    goOffline();
    api.getNotifications.mockRejectedValueOnce(networkError());

    await notificationRepository.markAsRead("n-1");
    const result = await notificationRepository.getAll();

    expect(api.markNotificationAsRead).not.toHaveBeenCalled();
    expect(result).toMatchObject({ fromCache: true, data: [{ id: "n-1", status: NotificationStatus.READ }] });
  });

  it("forgets saved data on logout", async () => {
    api.getNotifications.mockResolvedValueOnce([notification]);
    await notificationRepository.getAll();

    await clearSnapshots(OWNER);
    api.getNotifications.mockRejectedValueOnce(networkError());

    await expect(notificationRepository.getAll()).rejects.toThrow("Network Error");
  });
});
