import { Home, AlertCircle, BarChart3, Settings, LogOut } from "lucide-react";

interface SidebarProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
  onLogout: () => void;
  canAccessSettings: boolean;
}

export function Sidebar({
  activeTab,
  onTabChange,
  onLogout,
  canAccessSettings,
}: SidebarProps) {
  const menuItems = [
    { id: "dashboard", icon: Home, label: "Dashboard" },
    { id: "incidents", icon: AlertCircle, label: "Incidents" },
    { id: "analytics", icon: BarChart3, label: "Analytics" },
    {
      id: "settings",
      icon: Settings,
      label: "Settings",
      disabled: !canAccessSettings,
    },
  ];

  return (
    <div className="w-20 bg-gradient-to-b from-dark-800 to-dark-900 border-r border-dark-700 flex flex-col items-center py-6 space-y-8">
      {/* Logo */}
      <div className="w-12 h-12 bg-gradient-to-br from-blue-500 to-blue-600 rounded-lg flex items-center justify-center text-white font-bold">
        UG
      </div>

      {/* Menu */}
      <nav className="flex-1 flex flex-col space-y-4">
        {menuItems.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              onClick={() => {
                if (!item.disabled) {
                  onTabChange(item.id);
                }
              }}
              disabled={item.disabled}
              className={`w-12 h-12 rounded-lg flex items-center justify-center transition-all ${
                activeTab === item.id
                  ? "bg-blue-600 text-white shadow-lg shadow-blue-600/50"
                  : item.disabled
                    ? "text-dark-600 cursor-not-allowed"
                    : "text-dark-400 hover:text-white hover:bg-dark-700"
              }`}
              title={item.disabled ? `${item.label} (admin only)` : item.label}
            >
              <Icon size={24} />
            </button>
          );
        })}
      </nav>

      {/* Logout */}
      <button
        onClick={onLogout}
        className="w-12 h-12 rounded-lg text-dark-400 hover:text-red-400 hover:bg-dark-700 flex items-center justify-center transition-all"
        title="Logout"
      >
        <LogOut size={24} />
      </button>
    </div>
  );
}
