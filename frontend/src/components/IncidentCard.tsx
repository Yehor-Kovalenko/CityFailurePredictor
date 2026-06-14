import { useState } from "react";
import { CheckCircle, AlertCircle, Clock, Trash2 } from "lucide-react";
import { Incident, IncidentStatus } from "../types";
import {incidentService} from "../services/api";
import {
  INCIDENT_TYPE_COLORS,
  INCIDENT_STATUS_COLORS,
  INCIDENT_TYPE_ICONS,
  INCIDENT_STATUS_ICONS,
} from "../constants";
import {formatTimestamp, formatDate, shortenText} from "../utils/formatters";
import {publish} from "@/utils/eventBroker.ts";
import {EventFeedItemLevel} from "@/components/HeartbeatFeed.tsx";

interface IncidentCardProps {
  incident: Incident;
  onUpdate: () => void;
}

export function IncidentCard({ incident, onUpdate }: IncidentCardProps) {
  const [loading, setLoading] = useState(false);
  const [showDetails, setShowDetails] = useState(false);

  const statusOptions: IncidentStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED"];

  const handleStatusChange = async (newStatus: IncidentStatus) => {
    if (newStatus === incident.status) return;

    setLoading(true);
    try {
      await incidentService.updateIncidentStatus(incident.id, {
        status: newStatus,
      });
      onUpdate();
      publish('event_feed', {
        title: "Incident status updated",
        summary: `${newStatus}`,
        eventLevel: EventFeedItemLevel.INFO,
        type: "Incidents"
      });
    } catch (error) {
      console.error("Failed to update status:", error);
      publish('event_feed', {
        title: "Failed to update incident status",
        eventLevel: EventFeedItemLevel.ERROR,
        type: "Incidents"
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (window.confirm("Are you sure you want to delete this incident?")) {
      setLoading(true);
      try {
        await incidentService.deleteIncident(incident.id);
        onUpdate();
        publish('event_feed', {
          title: "Incident deleted",
          eventLevel: EventFeedItemLevel.INFO,
          type: "Incidents"
        });
      } catch (error) {
        console.error("Failed to delete incident:", error);
        alert("Failed to delete incident");
      } finally {
        setLoading(false);
      }
    }
  };

  const getStatusIcon = (status: IncidentStatus) => {
    switch (status) {
      case "OPEN":
        return <AlertCircle className="w-5 h-5" />;
      case "IN_PROGRESS":
        return <Clock className="w-5 h-5" />;
      case "RESOLVED":
        return <CheckCircle className="w-5 h-5" />;
    }
  };

  return (
    <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl hover:border-dark-500 transition-all p-4">
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-start gap-3 flex-1">
          <span className="text-3xl">
            {INCIDENT_TYPE_ICONS[incident.incidentType]}
          </span>
          <div className="flex-1 min-w-0">
            <h3 className="font-semibold truncate">
              {incident.incidentTitle}
            </h3>
            <p className="text-sm text-gray-500 line-clamp-2">
              {shortenText(incident.incidentSummary, 30)}
            </p>
          </div>
        </div>
        <button
          onClick={() => handleDelete()}
          disabled={loading}
          className="p-1 text-red-600 hover:bg-red-50 rounded disabled:opacity-50"
          title="Delete incident"
        >
          <Trash2 size={18} />
        </button>
      </div>

      <div className="flex flex-wrap gap-2 mb-3">
        <span
          className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${INCIDENT_TYPE_COLORS[incident.incidentType]}`}
        >
          {incident.incidentType}
        </span>
        <span
          className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${INCIDENT_STATUS_COLORS[incident.status]}`}
        >
          {INCIDENT_STATUS_ICONS[incident.status]} {incident.status}
        </span>
      </div>

      <div className="mb-3 p-2 bg-gray-700 rounded text-sm">
        <div className="flex items-center gap-2">
          <span>📍</span>
          <span>
            {incident.coordinates.x}, {incident.coordinates.y}
          </span>
        </div>
      </div>

      <button
        onClick={() => setShowDetails(!showDetails)}
        className="w-full text-left text-sm text-blue-600 hover:text-blue-800 mb-3"
      >
        {showDetails ? "Hide" : "Show"} Details
      </button>

      {showDetails && (
        <div className="mb-3 p-3 bg-gray-50 rounded text-xs text-gray-600 space-y-1 border-l-2 border-gray-300">
          <div>
            <span className="font-medium">Created:</span>{" "}
            {formatDate(incident.timestamp)}
          </div>
          <div>
            <span className="font-medium">Last Updated:</span>{" "}
            {formatTimestamp(incident.lastUpdated)}
          </div>
          <div>
            <span className="font-medium">ID:</span> {incident.id}
          </div>
        </div>
      )}

      <div className="space-y-2">
        <label className="block text-xs font-medium">
          Update Status
        </label>
        <div className="flex gap-2">
          {statusOptions.map((status) => (
            <button
              key={status}
              onClick={() => handleStatusChange(status)}
              disabled={loading}
              className={`flex-1 px-2 py-1 text-xs font-medium rounded transition-colors ${
                incident.status === status
                  ? `${INCIDENT_STATUS_COLORS[status]} border-2 border-current`
                  : "bg-gray-100 text-gray-700 hover:bg-gray-200"
              } disabled:opacity-50`}
            >
              {getStatusIcon(status)}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
