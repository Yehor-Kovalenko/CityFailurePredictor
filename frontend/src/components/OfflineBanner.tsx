import { CloudOff } from "lucide-react";
import { formatTimestamp } from "../utils/formatters";

interface OfflineBannerProps {
  online: boolean;
  cachedAt: number | null;
  pending: number;
}

export function OfflineBanner({ online, cachedAt, pending }: OfflineBannerProps) {
  if (online && cachedAt === null) {
    return null;
  }

  return (
    <div
      role="status"
      className="flex items-start gap-3 rounded-xl border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm text-amber-200"
    >
      <CloudOff size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
      <p>
        <span className="font-semibold">
          {online ? "Server unreachable." : "You are offline."}
        </span>{" "}
        {cachedAt !== null
          ? `Showing data saved ${formatTimestamp(cachedAt)}. `
          : ""}
        {pending > 0
          ? `${pending} change${pending === 1 ? "" : "s"} will sync when the connection returns.`
          : "New incidents and status changes are saved on this device and synced later."}
      </p>
    </div>
  );
}
