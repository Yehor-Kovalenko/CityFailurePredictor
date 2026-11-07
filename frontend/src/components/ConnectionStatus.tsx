import { CloudOff, RefreshCw } from "lucide-react";
import { useSyncStatus } from "../hooks/useSyncStatus";
import { requestSync } from "../offline/syncManager";

export function ConnectionStatus() {
  const { online, pending, syncing, lastError } = useSyncStatus();

  if (online && pending === 0 && !syncing) {
    return null;
  }

  const pendingLabel = `${pending} change${pending === 1 ? "" : "s"} waiting to sync`;

  return (
    <div role="status" aria-live="polite" className="flex items-center gap-1">
      {!online && (
        <span
          className="flex items-center gap-1 px-2 py-1 rounded-lg bg-amber-500/20 text-amber-400 text-sm font-medium whitespace-nowrap"
          title="You are offline. Changes are saved on this device and sent when the connection returns."
        >
          <CloudOff size={16} aria-hidden="true" />
          <span className="hidden sm:inline">Offline</span>
          <span className="sr-only sm:hidden">Offline</span>
        </span>
      )}
      {(pending > 0 || syncing) && (
        <button
          type="button"
          onClick={() => void requestSync()}
          disabled={!online || syncing}
          aria-label={syncing ? "Synchronizing changes" : `${pendingLabel}. Sync now`}
          title={lastError ?? (online ? "Sync now" : pendingLabel)}
          className="flex items-center gap-1 px-2 py-1 rounded-lg bg-blue-500/20 text-blue-300 text-sm font-medium whitespace-nowrap hover:bg-blue-500/30 disabled:cursor-default disabled:hover:bg-blue-500/20"
        >
          <RefreshCw size={16} aria-hidden="true" className={syncing ? "animate-spin" : ""} />
          <span data-testid="pending-count">{pending}</span>
          <span className="hidden md:inline">pending</span>
        </button>
      )}
    </div>
  );
}
