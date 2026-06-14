import { IncidentStatus, IncidentType } from "../types";

interface FilterBarProps {
  selectedStatus: IncidentStatus | undefined;
  selectedType: IncidentType | undefined;
  onStatusChange: (status: IncidentStatus | undefined) => void;
  onTypeChange: (type: IncidentType | undefined) => void;
}

const STATUSES: IncidentStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED"];
const TYPES: IncidentType[] = [
  "ELECTRICITY",
  "WATER",
  "ROADS",
  "FIRE",
  "ACCIDENT",
  "FLOOD",
];

export function FilterBar({
  selectedStatus,
  selectedType,
  onStatusChange,
  onTypeChange,
}: FilterBarProps) {
  return (
    <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl hover:border-dark-500 transition-all p-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium mb-2">
            Filter by Status
          </label>
          <select
            value={selectedStatus || ""}
            onChange={(e) =>
              onStatusChange((e.target.value as IncidentStatus) || undefined)
            }
            className="w-full px-3 py-2 border bg-dark-900 text-gray-300 border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">All Statuses</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-2">
            Filter by Type
          </label>
          <select
            value={selectedType || ""}
            onChange={(e) =>
              onTypeChange((e.target.value as IncidentType) || undefined)
            }
            className="w-full px-3 py-2 border bg-dark-900 text-gray-300 border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">All Types</option>
            {TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </div>
      </div>

      {(selectedStatus || selectedType) && (
        <button
          onClick={() => {
            onStatusChange(undefined);
            onTypeChange(undefined);
          }}
          className="mt-3 text-sm text-blue-600 hover:text-blue-800 font-medium"
        >
          Clear Filters
        </button>
      )}
    </div>
  );
}
