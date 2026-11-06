import { useEffect } from "react";
import { useRegisterSW } from "virtual:pwa-register/react";
import { useSyncStatus } from "../hooks/useSyncStatus";
import { requestSync } from "../offline/syncManager";

export const UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000;

export function UpdatePrompt() {
  const { pending, online } = useSyncStatus();
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    offlineReady: [offlineReady, setOfflineReady],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      if (!registration) return;
      window.setInterval(() => {
        if (navigator.onLine) void registration.update();
      }, UPDATE_CHECK_INTERVAL_MS);
    },
  });

  useEffect(() => {
    if (!offlineReady) return;
    const timer = window.setTimeout(() => setOfflineReady(false), 6000);
    return () => window.clearTimeout(timer);
  }, [offlineReady, setOfflineReady]);

  if (!needRefresh && !offlineReady) {
    return null;
  }

  const update = async () => {
    if (online && pending > 0) {
      await requestSync();
    }
    await updateServiceWorker(true);
  };

  return (
    <div
      role="alertdialog"
      aria-labelledby="pwa-toast-title"
      aria-describedby="pwa-toast-message"
      className="fixed z-[100001] bottom-20 md:bottom-4 inset-x-3 sm:inset-x-auto sm:right-4 sm:w-96 rounded-xl border border-dark-600 bg-dark-800 p-4 shadow-2xl text-dark-100"
    >
      <p id="pwa-toast-title" className="font-semibold">
        {needRefresh ? "A new version is available" : "Ready to work offline"}
      </p>
      <p id="pwa-toast-message" className="mt-1 text-sm text-dark-300">
        {needRefresh
          ? pending > 0
            ? `Your ${pending} unsynced change${pending === 1 ? " is" : "s are"} kept on this device and will sync after the update.`
            : "Reload to get the latest features and fixes."
          : "The app has been saved on this device and will open without a connection."}
      </p>
      <div className="mt-3 flex justify-end gap-2">
        <button
          type="button"
          onClick={() => {
            setNeedRefresh(false);
            setOfflineReady(false);
          }}
          className="px-3 py-1.5 text-sm rounded-lg text-dark-300 hover:bg-dark-700"
        >
          {needRefresh ? "Later" : "OK"}
        </button>
        {needRefresh && (
          <button
            type="button"
            onClick={() => void update()}
            className="px-3 py-1.5 text-sm rounded-lg bg-blue-600 text-white hover:bg-blue-700"
          >
            Reload
          </button>
        )}
      </div>
    </div>
  );
}
