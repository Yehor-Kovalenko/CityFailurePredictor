import {useEffect, useState} from "react";
import {AlertCircle, AlertTriangle, Info, Siren,} from "lucide-react";

import {Notification, NotificationSeverity, NotificationStatus,} from "@/types";
import {AlertPermissionButton} from "@/components/AlertPermissionButton";

interface Props {
    loadNotifications: () => Promise<Notification[]>;
    markAsRead: (id: string) => Promise<void>;
}

export default function NotificationDropdown({
                                                 loadNotifications,
                                                 markAsRead,
                                             }: Props) {
    const [loading, setLoading] = useState(false);
    const [notifications, setNotifications] = useState<Notification[]>([]);

    useEffect(() => {
        setLoading(true);

        loadNotifications()
            .then((data) =>
                setNotifications(
                    [...data].sort(
                        (a, b) =>
                            new Date(b.createdAt).getTime() -
                            new Date(a.createdAt).getTime()
                    )
                )
            )
            .catch((error) => {
                console.error("Failed to load notifications:", error);
                setNotifications([]);
            })
            .finally(() => setLoading(false));
    }, [loadNotifications]);

    const handleClick = async (notification: Notification) => {
        if (notification.status === NotificationStatus.READ) {
            return;
        }

        setNotifications((prev) =>
            prev.map((n) =>
                n.id === notification.id
                    ? {...n, status: NotificationStatus.READ}
                    : n,
            ),
        );

        try {
            await markAsRead(notification.id);
        } catch {
            setNotifications((prev) =>
                prev.map((n) =>
                    n.id === notification.id
                        ? {...n, status: NotificationStatus.UNREAD}
                        : n,
                ),
            );
        }
    };

    const severityIcon = (severity: NotificationSeverity) => {
        switch (severity) {
            case NotificationSeverity.LOW:
                return <Info size={18} className="text-blue-500"/>;
            case NotificationSeverity.MEDIUM:
                return <AlertCircle size={18} className="text-yellow-500"/>;
            case NotificationSeverity.HIGH:
                return <AlertTriangle size={18} className="text-orange-500"/>;
            case NotificationSeverity.CRITICAL:
                return <Siren size={18} className="text-red-600"/>;
            default:
                return <Info size={18} className="text-gray-500"/>;
        }
    };

    const buildShortMessage = (notification: Notification): string => {
        const kwh =
            notification.predictedKwh !== null &&
            notification.predictedKwh !== undefined
                ? `${notification.predictedKwh.toFixed(2)} kWh`
                : "unknown kWh";

        const risk =
            notification.riskScore !== null &&
            notification.riskScore !== undefined
                ? `Risk ${notification.riskScore.toFixed(2)}`
                : "Risk unknown";

        return `Household ${notification.householdId} • ${kwh} • ${risk}`;
    };

    return (
        <div
            // Below sm the panel spans the viewport so it never overflows the
            // left edge; from sm up it hangs off the bell icon.
            className="fixed inset-x-3 top-16 sm:absolute sm:inset-x-auto sm:right-0 sm:top-10 sm:w-96 z-[10000] rounded-xl border border-dark-600 bg-white shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between gap-2 border-b px-4 py-3 font-semibold text-black">
                Notifications
                <AlertPermissionButton/>
            </div>

            <div className="max-h-[60dvh] sm:max-h-96 overflow-y-auto overscroll-contain">
                {loading && (
                    <div className="p-4 text-center text-gray-500">
                        Loading...
                    </div>
                )}

                {!loading && notifications.length === 0 && (
                    <div className="p-6 text-center text-gray-500">
                        No notifications
                    </div>
                )}

                {!loading &&
                    notifications.map((notification) => (
                        <button
                            key={notification.id}
                            onClick={() => handleClick(notification)}
                            className={`flex w-full gap-3 border-b border-gray-200 p-4 text-left hover:bg-gray-50 ${
                                notification.status === NotificationStatus.UNREAD
                                    ? "bg-blue-50"
                                    : "bg-white"
                            }`}
                        >
                            <div className="mt-1 shrink-0">
                                {severityIcon(notification.severity)}
                            </div>

                            <div className="min-w-0 flex-1">
                                <div className="truncate text-sm font-semibold text-black">
                                    {notification.title}
                                </div>

                                <div className="mt-1 text-sm text-gray-700 overflow-hidden text-ellipsis">
                                    {buildShortMessage(notification)}
                                </div>

                                <div className="mt-2 flex items-center gap-2 text-xs text-gray-400">
                                    <span>{notification.severity}</span>
                                    <span>•</span>
                                    <span>
                        {new Date(notification.createdAt).toLocaleString()}
                    </span>
                                </div>
                            </div>

                            {notification.status === NotificationStatus.UNREAD && (
                                <div className="mt-2 h-2 w-2 shrink-0 rounded-full bg-blue-600"/>
                            )}
                        </button>
                    ))}
            </div>
        </div>
    );
}