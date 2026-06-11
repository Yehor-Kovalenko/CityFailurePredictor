import { useState, useEffect } from "react";
import { Sidebar } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { HeartbeatFeed } from "./components/HeartbeatFeed";
import { MapView } from "./components/MapView";
import { SystemHealth } from "./components/SystemHealth";
import { CreateIncidentModal } from "./components/CreateIncidentModal";
import { LoginScreen } from "./components/LoginScreen";
import { Incident } from "./types";
import incidentService from "./services/api";
import { useAuth } from "./auth/AuthContext";

const MOCK_INCIDENTS: Incident[] = [
  {
    id: "mock-1",
    incidentTitle: "Water Main Burst",
    incidentSummary:
      "Major water main rupture on Marszałkowska Street causing flooding",
    coordinates: { crs: "EPSG:4326", x: "51.7589", y: "19.4560" },
    incidentType: "WATER",
    status: "OPEN",
    timestamp: new Date().toISOString(),
    lastUpdated: new Date().toISOString(),
  },
  {
    id: "mock-2",
    incidentTitle: "Traffic Accident",
    incidentSummary:
      "Multi-vehicle collision on Al. Jerozolimskie with injuries reported",
    coordinates: { crs: "EPSG:4326", x: "51.7610", y: "19.4480" },
    incidentType: "ACCIDENT",
    status: "IN_PROGRESS",
    timestamp: new Date(Date.now() - 300000).toISOString(),
    lastUpdated: new Date().toISOString(),
  },
  {
    id: "mock-3",
    incidentTitle: "Building Fire",
    incidentSummary:
      "Fire reported in residential building, firefighters on scene",
    coordinates: { crs: "EPSG:4326", x: "51.7550", y: "19.4650" },
    incidentType: "FIRE",
    status: "IN_PROGRESS",
    timestamp: new Date(Date.now() - 600000).toISOString(),
    lastUpdated: new Date().toISOString(),
  },
  {
    id: "mock-4",
    incidentTitle: "Power Outage",
    incidentSummary: "Electrical outage affecting downtown district",
    coordinates: { crs: "EPSG:4326", x: "51.7700", y: "19.4400" },
    incidentType: "ELECTRICITY",
    status: "RESOLVED",
    timestamp: new Date(Date.now() - 1800000).toISOString(),
    lastUpdated: new Date().toISOString(),
  },
  {
    id: "mock-5",
    incidentTitle: "Road Collapse",
    incidentSummary: "Sinkhole discovered on Pulawskiego Street",
    coordinates: { crs: "EPSG:4326", x: "51.7520", y: "19.4520" },
    incidentType: "ROADS",
    status: "OPEN",
    timestamp: new Date(Date.now() - 900000).toISOString(),
    lastUpdated: new Date().toISOString(),
  },
  {
    id: "mock-6",
    incidentTitle: "Flooding Alert",
    incidentSummary: "Heavy rain causing flooding in Wawer district",
    coordinates: { crs: "EPSG:4326", x: "51.7750", y: "19.4750" },
    incidentType: "FLOOD",
    status: "OPEN",
    timestamp: new Date(Date.now() - 1200000).toISOString(),
    lastUpdated: new Date().toISOString(),
  },
];

function App() {
  const {
    session,
    isAuthenticated,
    isLoading,
    loginWithGoogle,
    logout,
    hasRole,
  } = useAuth();
  const [incidents, setIncidents] = useState<Incident[]>(MOCK_INCIDENTS);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedIncident, setSelectedIncident] = useState<Incident | null>(
    null,
  );
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [activeTab, setActiveTab] = useState("dashboard");
  const canAccessSettings = hasRole("ROLE_ADMIN");

  useEffect(() => {
    if (activeTab === "settings" && !canAccessSettings) {
      setActiveTab("dashboard");
    }
  }, [activeTab, canAccessSettings]);

  const handleLogin = async () => {
    setAuthError(null);
    setAuthLoading(true);
    try {
      await loginWithGoogle();
    } catch (error) {
      setAuthError(
        error instanceof Error
          ? error.message
          : "Authentication failed. Please try again.",
      );
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    setIncidents(MOCK_INCIDENTS);
    setSelectedIncident(null);
    setActiveTab("dashboard");
  };

  const loadIncidents = async () => {
    try {
      const data = await incidentService.getAllIncidents();
      setIncidents(data);
    } catch (error) {
      console.error("Failed to load incidents:", error);
    }
  };

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    loadIncidents();
  }, [isAuthenticated]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-dark-950 text-white flex items-center justify-center">
        Loading authentication...
      </div>
    );
  }

  if (!isAuthenticated || !session) {
    return (
      <LoginScreen
        onLogin={handleLogin}
        loading={authLoading}
        error={authError}
      />
    );
  }

  const filteredIncidents = incidents.filter(
    (incident) =>
      incident.incidentTitle
        .toLowerCase()
        .includes(searchQuery.toLowerCase()) ||
      incident.incidentSummary
        ?.toLowerCase()
        .includes(searchQuery.toLowerCase()) ||
      incident.coordinates.x.includes(searchQuery) ||
      incident.coordinates.y.includes(searchQuery),
  );

  return (
    <div className="flex h-screen bg-dark-950 overflow-hidden">
      {/* Sidebar */}
      <Sidebar
        activeTab={activeTab}
        onTabChange={setActiveTab}
        canAccessSettings={canAccessSettings}
      />

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Bar */}
        <TopBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onCreateClick={() => setIsModalOpen(true)}
          onLogout={() => {
            void handleLogout();
          }}
          userEmail={session.user.email}
          userRole={session.user.role}
        />

        {/* Main Layout */}
        <div className="flex-1 flex overflow-hidden">
          {/* Center Content */}
          <div className="flex-1 overflow-auto flex flex-col gap-6 p-6">
            {activeTab === "dashboard" && (
              <>
                {/* System Status Cards */}
                <SystemHealth incidents={filteredIncidents} />

                {/* Map View */}
                <div className="flex-1 min-h-96">
                  <MapView
                    incidents={filteredIncidents}
                    selectedIncident={selectedIncident}
                    onSelectIncident={setSelectedIncident}
                  />
                </div>
              </>
            )}

            {activeTab === "incidents" && (
              <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl p-6">
                <h2 className="text-2xl font-bold text-white mb-6">
                  All Incidents
                </h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                  {filteredIncidents.length === 0 ? (
                    <p className="col-span-full text-dark-400">
                      No incidents found
                    </p>
                  ) : (
                    filteredIncidents.map((incident) => (
                      <div
                        key={incident.id}
                        onClick={() => setSelectedIncident(incident)}
                        className={`p-4 rounded-lg border cursor-pointer transition-all ${
                          selectedIncident?.id === incident.id
                            ? "bg-blue-500/20 border-blue-500"
                            : "bg-dark-700 border-dark-600 hover:border-dark-500"
                        }`}
                      >
                        <p className="font-bold text-white">
                          {incident.incidentTitle}
                        </p>
                        <p className="text-sm text-dark-300 mt-2">
                          {incident.incidentSummary}
                        </p>
                        <div className="flex gap-2 mt-3">
                          <span className="text-xs px-2 py-1 bg-dark-600 text-dark-200 rounded">
                            {incident.incidentType}
                          </span>
                          <span
                            className={`text-xs px-2 py-1 rounded ${
                              incident.status === "OPEN"
                                ? "bg-red-500/20 text-red-400"
                                : incident.status === "IN_PROGRESS"
                                  ? "bg-yellow-500/20 text-yellow-400"
                                  : "bg-green-500/20 text-green-400"
                            }`}
                          >
                            {incident.status}
                          </span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {activeTab === "analytics" && (
              <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl p-6">
                <h2 className="text-2xl font-bold text-white">Analytics</h2>
                <p className="text-dark-400 mt-4">
                  Analytics dashboard coming soon...
                </p>
              </div>
            )}

            {activeTab === "settings" && (
              <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl p-6">
                <h2 className="text-2xl font-bold text-white">Settings</h2>
                {canAccessSettings ? (
                  <p className="text-dark-400 mt-4">
                    Settings page coming soon...
                  </p>
                ) : (
                  <p className="text-red-300 mt-4">
                    You are not authorized to access settings.
                  </p>
                )}
              </div>
            )}
          </div>

          {/* Right Sidebar - Heartbeat Feed */}
          <HeartbeatFeed incidents={filteredIncidents} alerts={[]} />
        </div>
      </div>

      {/* Modal */}
      <CreateIncidentModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={() => {
          loadIncidents();
          setIsModalOpen(false);
        }}
      />
    </div>
  );
}

export default App;
