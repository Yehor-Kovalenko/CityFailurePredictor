import {AlertCircle, Info, Skull, XCircle} from "lucide-react";
import { Incident } from "../types";
import { formatTimestamp } from "../utils/formatters";
import {useState} from "react";
import {useSubscribe} from "@/utils/eventBroker.ts";

interface HeartbeatFeedProps {
  incidents: Incident[];
  alerts: any[];
}

export enum EventFeedItemLevel {
  INFO, WARNING, ERROR, CRITICAL
}
export interface EventFeedItem {
  title: string,
  id?: string;
  summary?: string,
  type?: string,
  eventLevel?: EventFeedItemLevel // INFO, WARNING, ERROR, CRITICAL. Default INFO
  timestamp: number,
}


const eventLevelConfig = {
  [EventFeedItemLevel.INFO]: {
    icon: Info,
    color: "bg-blue-500",
    textColor: "text-blue-400",
  },
  [EventFeedItemLevel.WARNING]: {
    icon: AlertCircle ,
    color: "bg-yellow-500",
    textColor: "text-yellow-400",
  },
  [EventFeedItemLevel.ERROR]: {
    icon: XCircle,
    color: "bg-red-500",
    textColor: "text-red-400",
  },
  [EventFeedItemLevel.CRITICAL]: {
    icon: Skull,
    color: "bg-purple-600",
    textColor: "text-purple-400",
  },
};


export function HeartbeatFeed({ incidents }: HeartbeatFeedProps) {
  const openIncidents = incidents
    .filter((i) => i.status === "OPEN")
    .slice(0, 5);

  const [eventsFeed, setEventsFeed] = useState<EventFeedItem[]>([]);

  useSubscribe('event_feed', (data: EventFeedItem) => {
    if (!data) {
      return;
    }

    const timestamp: number = Date.now();
    data.timestamp = timestamp;
    data.id = crypto.randomUUID();
    setEventsFeed(prev => [data, ...prev]);
  });

  return (
    <div className="w-80 bg-gradient-to-b from-dark-800 to-dark-900 border-l border-dark-700 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="border-b border-dark-700 px-6 py-4">
        <h2 className="text-lg font-bold text-white">HEARTBEAT FEED</h2>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        {/* Alerts Section */}
        <div className="border-b border-dark-700">
          <div className="px-6 py-3 bg-dark-900/50">
            <h3 className="text-xs font-bold text-orange-500 uppercase tracking-wider">
              Predictive Alerts
            </h3>
          </div>
          <div className="divide-y divide-dark-700">
            {openIncidents.slice(0, 2).map((incident) => (
              <div
                key={incident.id}
                className="px-6 py-4 hover:bg-dark-700/50 transition-colors cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  <AlertCircle className="w-5 h-5 text-orange-500 flex-shrink-0 mt-0.5" />
                  {/*<div className="flex-1 min-w-0">*/}
                  {/*  <p className="text-sm font-semibold text-white line-clamp-1">*/}
                  {/*    {incident.incidentTitle}*/}
                  {/*  </p>*/}
                  {/*  <p className="text-xs text-dark-400 mt-1">*/}
                  {/*    {incident.incidentSummary || "No details available"}*/}
                  {/*  </p>*/}
                  {/*  <p className="text-xs text-dark-500 mt-1">*/}
                  {/*    {formatTimestamp(incident.timestamp)}*/}
                  {/*  </p>*/}
                  {/*</div>*/}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Events feed logs Section */}
        <div>
          <div className="px-6 py-3 bg-dark-900/50">
            <h3 className="text-xs font-bold text-blue-500 uppercase tracking-wider">
              Events Logs
            </h3>
          </div>
          <div className="divide-y divide-dark-700">
            {eventsFeed.map((event: EventFeedItem) => {
              const config = eventLevelConfig[event.eventLevel ?? EventFeedItemLevel.INFO];
              const Icon = config.icon;

              return <div
                  key={event.id}
                  className="px-6 py-4 hover:bg-dark-700/50 transition-colors cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  {/* ICON */}
                  <Icon className={`w-4 h-4 mt-1 ${config.textColor}`}/>

                  {/* CONTENT */}
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-white line-clamp-1">
                      {event.title}
                    </p>

                    <p className="text-xs text-dark-400 mt-1">
                      {event.summary || "No additional details"}
                    </p>

                    <p className={`text-xs mt-1 ${config.textColor}`}>
                      {formatTimestamp(event.timestamp)}
                    </p>
                  </div>
                </div>
              </div>
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
