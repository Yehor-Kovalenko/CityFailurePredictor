import { Search, Bell, LogOut, Plus } from "lucide-react";
import { UserRole } from "../types";

interface TopBarProps {
  searchQuery: string;
  onSearchChange: (query: string) => void;
  onCreateClick: () => void;
  onLogout: () => void;
  userEmail: string;
  userRole: UserRole;
}

export function TopBar({
  searchQuery,
  onSearchChange,
  onCreateClick,
  onLogout,
  userEmail,
  userRole,
}: TopBarProps) {
  return (
    <div className="bg-gradient-to-r from-dark-800 to-dark-800 border-b border-dark-700 px-6 py-4 flex items-center justify-between">
      <div className="flex-1 max-w-xl">
        <div className="relative">
          <Search
            className="absolute left-3 top-1/2 -translate-y-1/2 text-dark-500"
            size={20}
          />
          <input
            type="text"
            placeholder="Search asset ID or coordinates..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 placeholder-dark-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
          />
        </div>
      </div>

      <div className="flex items-center gap-4 ml-6">
        <div className="hidden lg:flex flex-col items-end px-3 py-1 rounded-lg bg-dark-700 border border-dark-600">
          <span className="text-xs text-dark-300">{userEmail}</span>
          <span className="text-xs text-blue-400">{userRole}</span>
        </div>

        <div className="flex items-center gap-1 px-3 py-1 bg-yellow-500/20 text-yellow-500 rounded-lg text-sm font-medium">
          <span className="w-2 h-2 bg-yellow-500 rounded-full"></span>3
          Predictive Warnings
        </div>

        <button className="p-2 hover:bg-dark-700 rounded-lg text-dark-400 hover:text-white transition-all">
          <Bell size={20} />
        </button>

        <button
          onClick={onCreateClick}
          className="flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-blue-600 to-blue-700 text-white rounded-lg hover:shadow-lg hover:shadow-blue-600/30 transition-all font-medium"
        >
          <Plus size={18} />
          New Incident
        </button>

        <button
          onClick={onLogout}
          className="p-2 hover:bg-dark-700 rounded-lg text-dark-400 hover:text-red-400 transition-all"
          title="Logout"
        >
          <LogOut size={20} />
        </button>
      </div>
    </div>
  );
}
