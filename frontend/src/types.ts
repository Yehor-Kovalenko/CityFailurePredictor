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
