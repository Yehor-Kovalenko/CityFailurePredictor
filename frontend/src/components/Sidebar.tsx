import { Home, AlertCircle, BarChart3, Settings } from "lucide-react";

interface SidebarProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
  canAccessSettings: boolean;
}

export function Sidebar({
  activeTab,
  onTabChange,
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
    <div className="shrink-0 w-full md:w-20 bg-gradient-to-b from-dark-800 to-dark-900 border-t md:border-t-0 md:border-r border-dark-700 flex md:flex-col items-center md:py-6 md:gap-8 pb-safe">
      {/* Logo */}
      <div
        className="hidden md:flex w-12 h-12 rounded-lg items-center justify-center text-white font-bold text-3xl"
        aria-hidden="true"
      >
        🏙️
      </div>

      {/* Menu */}
      <nav
        aria-label="Main navigation"
        className="flex-1 w-full flex md:flex-col justify-around md:justify-start items-center py-2 md:py-0 md:gap-4"
      >
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
              aria-label={item.label}
              aria-current={activeTab === item.id ? "page" : undefined}
            >
              <Icon size={24} />
            </button>
          );
        })}
      </nav>
    </div>
  );
}
