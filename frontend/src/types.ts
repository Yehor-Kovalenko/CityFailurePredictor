export interface Coordinates {
  crs: string;
  x: string;
  y: string;
}

export type IncidentType =
  | "ELECTRICITY"
  | "WATER"
  | "ROADS"
  | "FIRE"
  | "ACCIDENT"
  | "FLOOD";
export type IncidentStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED";

export interface Incident {
  id: string;
  incidentTitle: string;
  incidentSummary: string;
  coordinates: Coordinates;
  incidentType: IncidentType;
  status: IncidentStatus;
  timestamp: string;
  lastUpdated: string;
}

export interface CreateIncidentRequest {
  incidentTitle: string;
  incidentSummary: string;
  coordinates: Coordinates;
  incidentType: IncidentType;
}

export interface UpdateIncidentStatusRequest {
  status: IncidentStatus;
}

export interface ErrorResponse {
  code: string;
  message: string;
  timestamp: string;
}

export type UserRole = "ROLE_USER" | "ROLE_ADMIN";

export interface AuthUser {
  email: string;
  role: UserRole;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export interface AuthSession {
  accessToken: string;
  tokenType: string;
  expiresAt: number;
  user: AuthUser;
}

export enum NotificationSeverity {
  LOW = "LOW",
  MEDIUM = "MEDIUM",
  HIGH = "HIGH",
  CRITICAL = "CRITICAL",
}

export enum NotificationStatus {
  UNREAD = "UNREAD",
  READ = "READ",
}

export interface Notification {
  id: string,
  alertId: string,
  householdId: string,
  title: string,
  message: string,
  severity: NotificationSeverity,
  status: NotificationStatus,
  riskScore: number,
  predictedKwh: number,
  targetTimestamp: string,
  createdAt: string,
}