import { useEffect, useRef, useState } from "react";
// @ts-ignore
import {
    AlertCircle,
    AlertTriangle,
    Siren,
    Info,
} from "lucide-react";

import {
    Notification,
    NotificationSeverity,
    NotificationStatus,
} from "@/types";

interface Props {
    loadNotifications: () => Promise<Notification[]>;
    markAsRead: (id: string) => Promise<void>;
}

export default function NotificationDropdown({
                                                 loadNotifications,
                                                 markAsRead,
                                             }: Props) {
    const [open, setOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [notifications, setNotifications] = useState<Notification[]>([]);

    const ref = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;

        setLoading(true);

        loadNotifications()
            .then(setNotifications)
            .finally(() => setLoading(false));
    }, [open]);

    useEffect(() => {
        const listener = (event: MouseEvent) => {
            if (
                ref.current &&
                !ref.current.contains(event.target as Node)
            ) {
                setOpen(false);
            }
        };

        document.addEventListener("mousedown", listener);

        return () => {
            document.removeEventListener("mousedown", listener);
        };
    }, []);

    const handleClick = async (notification: Notification) => {
        if (notification.status === NotificationStatus.READ) {
            return;
        }

        setNotifications((prev) =>
            prev.map((n) =>
                n.id === notification.id
                    ? { ...n, status: NotificationStatus.READ }
                    : n
            )
        );

        try {
            await markAsRead(notification.id);
        } catch {
            setNotifications((prev) =>
                prev.map((n) =>
                    n.id === notification.id
                        ? { ...n, status: NotificationStatus.UNREAD }
                        : n
                )
            );
        }
    };

    const severityIcon = (severity: NotificationSeverity) => {
        switch (severity) {
            case NotificationSeverity.LOW:
                return <Info size={18} className="text-blue-500" />;

            case NotificationSeverity.MEDIUM:
                return (
                    <AlertCircle size={18} className="text-yellow-500" />
            );

            case NotificationSeverity.HIGH:
                return (
                    <AlertTriangle size={18} className="text-orange-500" />
            );

            case NotificationSeverity.CRITICAL:
                return (
                    <Siren size={18} className="text-red-600" />
            );
        }
    };

    return (
        <div
            className="
        absolute inline-block right-0 top-10 w-96
        rounded-lg
        border
        bg-white
        shadow-xl
        z-50
        "
        >
        <div className="border-b px-4 py-3 text-black font-semibold">
            Notifications
            </div>

            <div className="max-h-96 overflow-y-auto">
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
            onClick={() =>
            handleClick(notification)
        }
            className={`
                                        flex
                                        w-full
                                        gap-3
                                        border-b
                                        p-4
                                        text-left
                                        hover:bg-gray-50

                                        ${
                notification.status ===
                NotificationStatus.UNREAD
                    ? "bg-blue-50 font-medium"
                    : ""
            }
                                    `}
        >
            <div className="mt-1">
                {severityIcon(
                        notification.severity
        )}
            </div>

            <div className="flex-1">
            <div className="text-black">
                {notification.title}
            </div>

            <div className="mt-1 text-sm text-black">
            {notification.message}
            </div>

            <div className="mt-2 text-xs text-gray-400">
            {new Date(
                    notification.createdAt
                ).toLocaleString()}
            </div>
            </div>

            {notification.status ===
            NotificationStatus.UNREAD && (
                <div className="mt-2 h-2 w-2 rounded-full bg-blue-600" />
            )}
            </button>
        ))}
        </div>
        </div>
);
}