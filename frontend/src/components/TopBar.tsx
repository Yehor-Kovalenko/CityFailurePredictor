import {Activity, Bell, LogOut, Plus, Search} from "lucide-react";
import {UserRole} from "../types";
import React from "react";
import {ConnectionStatus} from "./ConnectionStatus";

interface TopBarProps {
    searchQuery: string;
    onSearchChange: (query: string) => void;
    onNotificationsClick: () => void;
    onCreateClick: () => void;
    onLogout: () => void;
    userEmail: string;
    userRole: UserRole;
    notificationDropdown?: React.ReactNode;
    isNotificationsListOpen?: boolean;
    onFeedToggle?: () => void;
    isFeedOpen?: boolean;
}

export function TopBar({
                           searchQuery,
                           onSearchChange,
                           onNotificationsClick,
                           onCreateClick,
                           onLogout,
                           userEmail,
                           userRole,
                           isNotificationsListOpen,
                           notificationDropdown,
                           onFeedToggle,
                           isFeedOpen = false,
                       }: TopBarProps) {
    return (
        <div
            className="relative z-[9999] bg-gradient-to-r from-dark-800 to-dark-800 border-b border-dark-700 px-3 sm:px-6 py-3 sm:py-4 pt-safe flex flex-wrap items-center justify-between gap-3">
            <div className="order-last sm:order-none basis-full sm:basis-auto flex-1 min-w-0 sm:max-w-xl">
                <div className="relative">
                    <Search
                        className="absolute left-3 top-1/2 -translate-y-1/2 text-dark-500"
                        size={20}
                    />
                    <input
                        type="text"
                        placeholder="Search asset ID or coordinates..."
                        aria-label="Search incidents"
                        value={searchQuery}
                        onChange={(e) => onSearchChange(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 bg-dark-700 border border-dark-600 rounded-lg text-dark-100 placeholder-dark-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                    />
                </div>
            </div>

            <div className="flex items-center gap-1 sm:gap-3 lg:gap-4 ml-auto">
                <div
                    className="hidden lg:flex flex-col items-end max-w-[14rem] px-3 py-1 rounded-lg bg-dark-700 border border-dark-600">
                    <span className="text-xs text-dark-300 truncate max-w-full" title={userEmail}>{userEmail}</span>
                    <span className="text-xs text-blue-400">{userRole}</span>
                </div>

                <div
                    className="flex items-center gap-1 px-2 sm:px-3 py-1 bg-yellow-500/20 text-yellow-500 rounded-lg text-sm font-medium whitespace-nowrap"
                    title="3 Predictive Warnings">
                    <span className="w-2 h-2 bg-yellow-500 rounded-full"></span>3
                    <span className="hidden md:inline">Predictive Warnings</span>
                </div>

                <ConnectionStatus/>

                {onFeedToggle && (
                    <button
                        onClick={onFeedToggle}
                        aria-label="Toggle activity feed"
                        aria-expanded={isFeedOpen}
                        aria-controls="heartbeat-feed"
                        title="Activity feed"
                        className="xl:hidden p-2 hover:bg-dark-700 rounded-lg text-dark-400 hover:text-white transition-all"
                    >
                        <Activity size={20}/>
                    </button>
                )}

                <div className="relative z-[10000]">
                    <button
                        onClick={onNotificationsClick}
                        aria-label="Notifications"
                        aria-expanded={Boolean(isNotificationsListOpen)}
                        className="p-2 hover:bg-dark-700 rounded-lg text-dark-400 hover:text-white transition-all"
                    >
                        <Bell size={20}/>
                    </button>

                    {isNotificationsListOpen && notificationDropdown}
                </div>

                <button
                    onClick={onCreateClick}
                    aria-label="New Incident"
                    className="flex items-center gap-2 p-2 sm:px-4 bg-gradient-to-r from-blue-600 to-blue-700 text-white rounded-lg hover:shadow-lg hover:shadow-blue-600/30 transition-all font-medium whitespace-nowrap"
                >
                    <Plus size={18}/>
                    <span className="hidden sm:inline">New Incident</span>
                </button>

                <button
                    onClick={onLogout}
                    className="p-2 hover:bg-dark-700 rounded-lg text-dark-400 hover:text-red-400 transition-all"
                    title="Logout"
                    aria-label="Logout"
                >
                    <LogOut size={20}/>
                </button>
            </div>
        </div>
    );
}
