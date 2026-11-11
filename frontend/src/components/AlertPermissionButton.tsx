import { useState } from "react";
import { BellRing } from "lucide-react";
import { enablePush, supportsPush } from "@/notifications/alertNotifications";

export function AlertPermissionButton() {
  const [permission, setPermission] = useState<NotificationPermission | null>(
    supportsPush() ? Notification.permission : null,
  );
  const [busy, setBusy] = useState(false);

  if (permission === null || permission === "granted") {
    return null;
  }

  if (permission === "denied") {
    return (
      <span className="text-xs font-normal text-gray-500">
        Alerts blocked in browser
      </span>
    );
  }

  const handleClick = async () => {
    setBusy(true);
    try {
      setPermission(await enablePush());
    } catch (error) {
      console.error("Failed to enable push alerts:", error);
      setPermission(Notification.permission);
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={busy}
      className="flex items-center gap-1 rounded-md bg-blue-600 px-2 py-1 text-xs font-medium text-white hover:bg-blue-700 disabled:opacity-60"
    >
      <BellRing size={14} />
      Enable alerts
    </button>
  );
}
