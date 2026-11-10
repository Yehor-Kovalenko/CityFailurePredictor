import { useState, useEffect, useCallback } from "react";
import { Sidebar } from "./components/Sidebar";
import { TopBar } from "./components/TopBar";
import { HeartbeatFeed } from "./components/HeartbeatFeed";
import { MapView } from "./components/MapView";
import { SystemHealth } from "./components/SystemHealth";
import { CreateIncidentModal } from "./components/CreateIncidentModal";
import { LoginScreen } from "./components/LoginScreen";
import { EventFeedItem, EventFeedItemLevel, Incident } from "./types";
import {incidentRepository, notificationRepository} from "./offline/repository";
import {clearSnapshots} from "./offline/snapshots";
import {useSyncStatus} from "./hooks/useSyncStatus";
import {OfflineBanner} from "./components/OfflineBanner";
import { useAuth } from "./auth/AuthContext";
import {IncidentDashboard} from "./components/IncidentDashboard.tsx";
import NotificationDropdown from "@/components/NotificationDropdown.tsx";
import {publish} from "@/utils/eventBroker.ts";
import {useHideSplash} from "./hooks/useHideSplash";
import {disablePush, OPEN_NOTIFICATIONS_PARAM, syncPushSubscription} from "./notifications/alertNotifications";

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
  const [isNotificationsListOpen, setisNotificationsListOpen] = useState(() =>
    new URLSearchParams(window.location.search).has(OPEN_NOTIFICATIONS_PARAM),
  );
  const [activeTab, setActiveTab] = useState("dashboard");
  const [isFeedOpen, setIsFeedOpen] = useState(false);
  const closeFeed = useCallback(() => setIsFeedOpen(false), []);
  const [cachedAt, setCachedAt] = useState<number | null>(null);
  const canAccessSettings = hasRole("ROLE_ADMIN");
  const {online, pending} = useSyncStatus();
  useHideSplash(!isLoading);

  useEffect(() => {
    if (isAuthenticated) {
      syncPushSubscription().catch((error) =>
        console.error("Failed to sync push subscription:", error),
      );
    }
  }, [isAuthenticated]);

  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.has(OPEN_NOTIFICATIONS_PARAM)) {
      url.searchParams.delete(OPEN_NOTIFICATIONS_PARAM);
      window.history.replaceState(null, "", url);
    }

    const handleServiceWorkerMessage = (event: MessageEvent) => {
      if (event.data?.type === "OPEN_NOTIFICATIONS") {
        setisNotificationsListOpen(true);
      }
      if (event.data?.type === "ALERT_RECEIVED") {
        publish<EventFeedItem>("event_feed", {
          title: event.data.title,
          summary: event.data.body,
          eventLevel: EventFeedItemLevel.WARNING,
          timestamp: Date.now(),
        });
      }
    };
    navigator.serviceWorker?.addEventListener("message", handleServiceWorkerMessage);
    return () => navigator.serviceWorker?.removeEventListener("message", handleServiceWorkerMessage);
  }, []);

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
    const owner = session?.user.email;
    await disablePush().catch((error) =>
      console.error("Failed to remove push subscription:", error),
    );
    await logout();
    if (owner) {
      await clearSnapshots(owner).catch(() => undefined);
    }
    setIncidents(MOCK_INCIDENTS);
    setSelectedIncident(null);
    setActiveTab("dashboard");
  };

  const loadIncidents = useCallback(async () => {
    try {
      const result = await incidentRepository.getAll();
      setIncidents(result.data);
      setCachedAt(result.fromCache ? result.savedAt : null);
    } catch (error) {
      console.error("Failed to load incidents:", error);
    }
  }, []);

  const loadNotifications = useCallback(
    async () => (await notificationRepository.getAll()).data,
    [],
  );

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    void loadIncidents();
  }, [isAuthenticated, loadIncidents, online, pending]);

  if (isLoading) {
    return (
      <div className="min-h-dvh bg-dark-950 text-white flex items-center justify-center">
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
    // Mobile: content on top, navigation as a bottom bar (flex-col-reverse).
    // md and up: navigation rail on the left.
    <div className="flex flex-col-reverse md:flex-row h-dvh bg-dark-950 overflow-hidden">
      {/* Sidebar */}
      <Sidebar
        activeTab={activeTab}
        onTabChange={setActiveTab}
        canAccessSettings={canAccessSettings}
      />

      {/* Main Content */}
      <div className="flex-1 min-w-0 min-h-0 flex flex-col overflow-hidden">
        {/* Top Bar */}
        <TopBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onNotificationsClick={() => setisNotificationsListOpen(!isNotificationsListOpen)}
          onCreateClick={() => setIsModalOpen(true)}
          onLogout={() => {
            void handleLogout();
          }}
          userEmail={session.user.email}
          userRole={session.user.role}
          isNotificationsListOpen={isNotificationsListOpen}
          onFeedToggle={() => setIsFeedOpen((open) => !open)}
          isFeedOpen={isFeedOpen}
          notificationDropdown={<NotificationDropdown
                  loadNotifications={loadNotifications}
                  markAsRead={async (id) => {
                    await notificationRepository.markAsRead(id);
                    publish("events_feed", {
                      "title": "Notification marked as read"
                    })
                  }}
              />
          }
          />

        {/* Main Layout */}
        <div className="flex-1 min-h-0 flex overflow-hidden">
          {/* Center Content */}
          <main className="flex-1 min-w-0 overflow-auto flex flex-col gap-4 sm:gap-6 p-3 sm:p-6">
            <OfflineBanner online={online} cachedAt={cachedAt} pending={pending} />
            {activeTab === "dashboard" && (
              <>
                {/* System Status Cards */}
                <SystemHealth incidents={filteredIncidents} />

                {/* Map View */}
                <div className="flex-1 min-h-[60dvh] md:min-h-96">
                  <MapView
                    incidents={filteredIncidents}
                    selectedIncident={selectedIncident}
                    onSelectIncident={setSelectedIncident}
                  />
                </div>
              </>
            )}

            {activeTab === "incidents" && (
                <IncidentDashboard />
            )}

            {activeTab === "analytics" && (
              <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl p-4 sm:p-6">
                <h2 className="text-xl sm:text-2xl font-bold text-white">Analytics</h2>
                <p className="text-dark-400 mt-4">
                  Analytics dashboard coming soon...
                </p>
              </div>
            )}

            {activeTab === "settings" && (
              <div className="bg-gradient-to-br from-dark-700 to-dark-800 border border-dark-600 rounded-xl p-4 sm:p-6">
                <h2 className="text-xl sm:text-2xl font-bold text-white">Settings</h2>
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
          </main>

          {/* Right Sidebar - Heartbeat Feed */}
          <HeartbeatFeed
            incidents={filteredIncidents}
            alerts={[]}
            isOpen={isFeedOpen}
            onClose={closeFeed}
          />
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
