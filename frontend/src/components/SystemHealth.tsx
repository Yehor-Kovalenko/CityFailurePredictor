import { TrendingUp, Droplets } from "lucide-react";
import { Incident } from "../types";

interface SystemHealthProps {
  incidents: Incident[];
}

export function SystemHealth({ incidents }: SystemHealthProps) {
  const totalIncidents = incidents.length;
  const resolvedCount = incidents.filter((i) => i.status === "RESOLVED").length;
  const healthPercentage =
    totalIncidents > 0
      ? Math.round((resolvedCount / totalIncidents) * 100) + 50
      : 98;

  const openIncidents = incidents.filter((i) => i.status === "OPEN").length;
  const stressLevel = Math.min(
    100,
    (openIncidents / Math.max(1, totalIncidents)) * 100,
  );

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {/* System Health */}
      <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl p-6 hover:border-dark-500 transition-all">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-dark-300 uppercase tracking-wider">
            System Health
          </h3>
          <TrendingUp className="w-5 h-5 text-blue-500" />
        </div>
        <div className="flex items-end justify-between">
          <div>
            <p className="text-4xl font-bold text-white">{healthPercentage}%</p>
            <p className="text-sm text-dark-400 mt-1">
              Active sensor network status
            </p>
          </div>
          <div className="w-24 h-24 rounded-full bg-gradient-to-br from-blue-500/20 to-blue-600/20 flex items-center justify-center border border-blue-500/30">
            <span className="text-2xl font-bold text-blue-400">
              {healthPercentage}%
            </span>
          </div>
        </div>
      </div>

      {/* Weather Pulse */}
      <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl p-6 hover:border-dark-500 transition-all">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-dark-300 uppercase tracking-wider">
            Weather Pulse
          </h3>
          <Droplets className="w-5 h-5 text-cyan-500" />
        </div>
        <div className="flex items-end justify-between">
          <div>
            <p className="text-4xl font-bold text-white">12°C</p>
            <p className="text-sm text-dark-400 mt-1">
              Average city temperature
            </p>
          </div>
          <div className="flex flex-col items-end gap-2">
            <div className="px-3 py-1 bg-orange-500/20 text-orange-400 rounded-lg text-xs font-bold">
              STRESS: {Math.round(stressLevel)}%
            </div>
            <div className="w-16 h-16 rounded-full bg-gradient-to-br from-orange-500/20 to-orange-600/20 flex items-center justify-center border border-orange-500/30">
              <span className="text-lg font-bold text-orange-400">
                {Math.round(stressLevel)}%
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
