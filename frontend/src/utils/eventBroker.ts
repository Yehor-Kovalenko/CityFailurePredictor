import { useEffect } from 'react';

type EventCallback<T = unknown> = (data: T) => void;
const listeners = new Map<string, Set<EventCallback>>();

export function publish<T = unknown>(events: string | string[], data: T): void {
    function _notify_listeners(event: string, data: T) {
        listeners.get(event)?.forEach(cb => cb(data));
    }

    if (Array.isArray(events)) {
        events.forEach(event => _notify_listeners(event, data));
    } else {
        _notify_listeners(events, data);
    }

}

function subscribe<T = unknown>(event: string, callback: EventCallback<T>): () => void {
    if (!listeners.has(event)) listeners.set(event, new Set());
    listeners.get(event)!.add(callback as EventCallback);
    return () => listeners.get(event)?.delete(callback as EventCallback);
}

export function useSubscribe<T = unknown>(event: string, callback: (data: T) => void): void {
    useEffect(() => subscribe(event, callback), [event]);
}