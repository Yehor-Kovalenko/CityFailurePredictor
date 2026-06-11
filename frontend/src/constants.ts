import { IncidentType, IncidentStatus } from "./types";

export const INCIDENT_TYPE_COLORS: Record<IncidentType, string> = {
  FIRE: "bg-red-100 text-red-800",
  WATER: "bg-blue-100 text-blue-800",
  ELECTRICITY: "bg-amber-100 text-amber-800",
  ROADS: "bg-gray-100 text-gray-800",
  ACCIDENT: "bg-fuchsia-100 text-fuchsia-800",
  FLOOD: "bg-cyan-100 text-cyan-800",
};

export const INCIDENT_STATUS_COLORS: Record<IncidentStatus, string> = {
  OPEN: "bg-red-100 text-red-800",
  IN_PROGRESS: "bg-yellow-100 text-yellow-800",
  RESOLVED: "bg-green-100 text-green-800",
};

export const INCIDENT_TYPE_ICONS: Record<IncidentType, string> = {
  FIRE: "🔥",
  WATER: "💧",
  ELECTRICITY: "⚡",
  ROADS: "🛣️",
  ACCIDENT: "🚗",
  FLOOD: "🌊",
};

export const INCIDENT_STATUS_ICONS: Record<IncidentStatus, string> = {
  OPEN: "🔴",
  IN_PROGRESS: "🟡",
  RESOLVED: "🟢",
};
