import { useState } from "react";
import { X } from "lucide-react";
import { CreateIncidentRequest, Coordinates, IncidentType } from "../types";
import incidentService from "../services/api";

interface CreateIncidentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

const INCIDENT_TYPES: IncidentType[] = [
  "ELECTRICITY",
  "WATER",
  "ROADS",
  "FIRE",
  "ACCIDENT",
  "FLOOD",
];

export function CreateIncidentModal({
  isOpen,
  onClose,
  onSuccess,
}: CreateIncidentModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    incidentTitle: "",
    incidentSummary: "",
    x: "",
    y: "",
    crs: "EPSG:4326",
    incidentType: "FIRE" as IncidentType,
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const coordinates: Coordinates = {
        crs: formData.crs,
        x: formData.x,
        y: formData.y,
      };

      const request: CreateIncidentRequest = {
        incidentTitle: formData.incidentTitle,
        incidentSummary: formData.incidentSummary,
        coordinates,
        incidentType: formData.incidentType,
      };

      await incidentService.createIncident(request);
      onSuccess();
      onClose();
      setFormData({
        incidentTitle: "",
        incidentSummary: "",
        x: "",
        y: "",
        crs: "EPSG:4326",
        incidentType: "FIRE",
      });
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to create incident");
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-[100000]">
      <div className="bg-white rounded-lg shadow-lg max-w-md w-full mx-4">
        <div className="flex items-center justify-between p-6 border-b">
          <h2 className="text-xl font-semibold text-black">Create New Incident</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X size={24} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded">
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Incident Title
            </label>
            <input
              type="text"
              required
              value={formData.incidentTitle}
              onChange={(e) =>
                setFormData({ ...formData, incidentTitle: e.target.value })
              }
              className="w-full px-3 py-2 border text-black border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="e.g., Water Main Burst"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Description
            </label>
            <textarea
              value={formData.incidentSummary}
              onChange={(e) =>
                setFormData({ ...formData, incidentSummary: e.target.value })
              }
              className="w-full px-3 py-2 border text-black border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Describe the incident..."
              rows={3}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Incident Type
            </label>
            <select
              value={formData.incidentType}
              onChange={(e) =>
                setFormData({
                  ...formData,
                  incidentType: e.target.value as IncidentType,
                })
              }
              className="w-full px-3 py-2 border border-gray-300 text-black rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {INCIDENT_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                X Coordinate
              </label>
              <input
                type="number"
                step="0.0001"
                required
                value={formData.x}
                onChange={(e) =>
                  setFormData({ ...formData, x: e.target.value })
                }
                className="w-full px-3 py-2 border text-black border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="51.5074"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Y Coordinate
              </label>
              <input
                type="number"
                step="0.0001"
                required
                value={formData.y}
                onChange={(e) =>
                  setFormData({ ...formData, y: e.target.value })
                }
                className="w-full px-3 py-2 border text-black border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="19.4568"
              />
            </div>
          </div>

          <div className="flex gap-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-md hover:bg-gray-200"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
            >
              {loading ? "Creating..." : "Create Incident"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
