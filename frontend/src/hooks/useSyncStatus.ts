import { useSyncExternalStore } from "react";
import { getSyncState, subscribeSyncState, type SyncState } from "../offline/syncManager";

export function useSyncStatus(): SyncState {
  return useSyncExternalStore(subscribeSyncState, getSyncState);
}
