import { useState, useEffect } from "react";
import { IncidentCard } from "./IncidentCard";
import { FilterBar } from "./FilterBar";
import { CreateIncidentModal } from "./CreateIncidentModal";
import { Incident, IncidentStatus, IncidentType } from "../types";
import incidentService from "../services/api";
import { AlertCircle, Plus, RefreshCw } from "lucide-react";
import {publish} from "@/utils/eventBroker.ts";
import {shortenText} from "@/utils/formatters.ts";
import {EventFeedItemLevel} from "@/components/HeartbeatFeed.tsx";

export function IncidentDashboard() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedStatus, setSelectedStatus] = useState<
    IncidentStatus | undefined
  >();
  const [selectedType, setSelectedType] = useState<IncidentType | undefined>();
  const [isModalOpen, setIsModalOpen] = useState(false);

  const loadIncidents = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await incidentService.getAllIncidents(
        selectedStatus,
        selectedType,
      );
      setIncidents(data);
      publish('event_feed', {
        title: "Incidents loaded",
        eventLevel: EventFeedItemLevel.INFO,
        type: "Incidents"
      });
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to load incidents");
      publish('event_feed', {
        title: "Failed to load incidents",
        eventLevel: EventFeedItemLevel.ERROR,
        type: "Incidents"
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadIncidents();
  }, [selectedStatus, selectedType]);

  const openCounts = incidents.filter((i) => i.status === "OPEN").length;
  const inProgressCounts = incidents.filter(
    (i) => i.status === "IN_PROGRESS",
  ).length;
  const resolvedCounts = incidents.filter(
    (i) => i.status === "RESOLVED",
  ).length;

  return (
    <div className="min-h-screen to-gray-100">
      <div className="max-w-6xl mx-auto p-4">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between mb-2">
            <h1 className="text-4xl font-bold ">
              🏙️ City Failure Predictor
            </h1>
            <button
              onClick={loadIncidents}
              disabled={loading}
              className="p-2 rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
              title="Refresh incidents"
            >
              <RefreshCw size={20} className={loading ? "animate-spin" : ""} />
            </button>
          </div>
          <p className="text-gray-300">
            Real-time incident management and monitoring system
          </p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl hover:border-dark-500 transition-all p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm ">Total Incidents</p>
                <p className="text-3xl font-bold ">
                  {incidents.length}
                </p>
              </div>
              <span className="text-4xl">📊</span>
            </div>
          </div>

          <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl hover:border-dark-500 transition-all p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm">Open</p>
                <p className="text-3xl font-bold text-red-600">{openCounts}</p>
              </div>
              <span className="text-4xl">🔴</span>
            </div>
          </div>

          <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl hover:border-dark-500 transition-all p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm">In Progress</p>
                <p className="text-3xl font-bold text-yellow-600">
                  {inProgressCounts}
                </p>
              </div>
              <span className="text-4xl">🟡</span>
            </div>
          </div>

          <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl hover:border-dark-500 transition-all p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm">Resolved</p>
                <p className="text-3xl font-bold text-green-600">
                  {resolvedCounts}
                </p>
              </div>
              <span className="text-4xl">🟢</span>
            </div>
          </div>
        </div>

        {/* Create Button */}
        <div className="mb-6">
          <button
            onClick={() => setIsModalOpen(true)}
            className="flex items-center gap-2 px-6 py-3 bg-gradient-to-r from-blue-600 to-blue-700 text-white font-semibold rounded-lg hover:shadow-lg transition-all"
          >
            <Plus size={20} />
            Create New Incident
          </button>
        </div>

        {/* Error Message */}
        {error && (
          <div className="mb-6 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg flex items-center gap-3">
            <AlertCircle size={20} />
            <div>
              <p className="font-semibold">Error loading incidents</p>
              <p className="text-sm">{error}</p>
            </div>
          </div>
        )}

        {/* Filters */}
        <FilterBar
          selectedStatus={selectedStatus}
          selectedType={selectedType}
          onStatusChange={setSelectedStatus}
          onTypeChange={setSelectedType}
        />

        {/* Incidents Grid */}
        <div className="mt-6">
          {loading && !incidents.length ? (
            <div className="flex flex-col items-center justify-center py-12">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
              <p className="text-gray-600">Loading incidents...</p>
            </div>
          ) : incidents.length === 0 ? (
            <div className="bg-white rounded-lg shadow-md p-12 text-center">
              <p className="text-gray-600 text-lg">No incidents found</p>
              <p className="text-gray-500 text-sm mt-2">
                Create a new incident or adjust your filters
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {incidents.map((incident) => (
                <IncidentCard
                  key={incident.id}
                  incident={incident}
                  onUpdate={loadIncidents}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      <CreateIncidentModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={loadIncidents}
      />
    </div>
  );
}
