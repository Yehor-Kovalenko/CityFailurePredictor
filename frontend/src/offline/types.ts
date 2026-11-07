import type {
  CreateIncidentRequest,
  IncidentStatus,
} from "../types";

export type OutboxOperation =
  | { type: "createIncident"; localId: string; request: CreateIncidentRequest }
  | { type: "updateIncidentStatus"; incidentId: string; status: IncidentStatus }
  | { type: "deleteIncident"; incidentId: string }
  | { type: "markNotificationRead"; notificationId: string };

export interface OutboxEntry {
  seq?: number;
  owner: string;
  createdAt: number;
  attempts: number;
  lastError?: string;
  operation: OutboxOperation;
}

export interface Snapshot<T = unknown> {
  key: string;
  owner: string;
  savedAt: number;
  data: T;
}
