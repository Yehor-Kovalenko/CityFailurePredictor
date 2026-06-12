import { AlertCircle } from "lucide-react";
import { Incident } from "../types";
import { formatTimestamp } from "../utils/formatters";

interface HeartbeatFeedProps {
  incidents: Incident[];
  alerts: any[];
}

export function HeartbeatFeed({ incidents }: HeartbeatFeedProps) {
  const openIncidents = incidents
    .filter((i) => i.status === "OPEN")
    .slice(0, 5);

  return (
    <div className="w-80 bg-gradient-to-b from-dark-800 to-dark-900 border-l border-dark-700 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="border-b border-dark-700 px-6 py-4">
        <h2 className="text-lg font-bold text-white">HEARTBEAT FEED</h2>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        {/* Alerts Section */}
        <div className="border-b border-dark-700">
          <div className="px-6 py-3 bg-dark-900/50">
            <h3 className="text-xs font-bold text-orange-500 uppercase tracking-wider">
              Predictive Alerts
            </h3>
          </div>
          <div className="divide-y divide-dark-700">
            {openIncidents.slice(0, 2).map((incident) => (
              <div
                key={incident.id}
                className="px-6 py-4 hover:bg-dark-700/50 transition-colors cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  <AlertCircle className="w-5 h-5 text-orange-500 flex-shrink-0 mt-0.5" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-white line-clamp-1">
                      {incident.incidentTitle}
                    </p>
                    <p className="text-xs text-dark-400 mt-1">
                      {incident.incidentSummary || "No details available"}
                    </p>
                    <p className="text-xs text-dark-500 mt-1">
                      {formatTimestamp(incident.timestamp)}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Status Logs Section */}
        <div>
          <div className="px-6 py-3 bg-dark-900/50">
            <h3 className="text-xs font-bold text-blue-500 uppercase tracking-wider">
              Status Logs
            </h3>
          </div>
          <div className="divide-y divide-dark-700">
            {openIncidents.map((incident) => (
              <div
                key={incident.id}
                className="px-6 py-4 hover:bg-dark-700/50 transition-colors cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  <div className="w-2 h-2 bg-blue-500 rounded-full flex-shrink-0 mt-1.5"></div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-white line-clamp-1">
                      {incident.incidentType}
                    </p>
                    <p className="text-xs text-dark-400 mt-1">
                      Status: {incident.status}
                    </p>
                    <p className="text-xs text-dark-500 mt-1">
                      {formatTimestamp(incident.lastUpdated)}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
