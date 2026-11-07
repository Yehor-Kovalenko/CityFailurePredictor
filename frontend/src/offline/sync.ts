import { incidentService, notificationService } from "../services/api";
import { getErrorMessage } from "../utils/errors";
import { classifyFailure, httpStatusOf } from "./network";
import { listOutbox, recordFailedAttempt, removeFromOutbox } from "./outbox";
import type { OutboxEntry, OutboxOperation } from "./types";

export type Replayer = (operation: OutboxOperation) => Promise<unknown>;

export interface SyncReport {
  synced: number;
  rejected: { entry: OutboxEntry; reason: string }[];
  remaining: number;
  stoppedBy?: "retry" | "auth";
}

export const replayOperation: Replayer = (operation) => {
  switch (operation.type) {
    case "createIncident":
      return incidentService.createIncident(operation.request);
    case "updateIncidentStatus":
      return incidentService.updateIncidentStatus(operation.incidentId, {
        status: operation.status,
      });
    case "deleteIncident":
      return incidentService.deleteIncident(operation.incidentId);
    case "markNotificationRead":
      return notificationService.markNotificationAsRead(operation.notificationId);
  }
};

function alreadyApplied(operation: OutboxOperation, error: unknown): boolean {
  return (
    httpStatusOf(error) === 404 &&
    (operation.type === "deleteIncident" || operation.type === "markNotificationRead")
  );
}

export async function syncOutbox(
  owner: string,
  replay: Replayer = replayOperation,
): Promise<SyncReport> {
  const entries = await listOutbox(owner);
  const report: SyncReport = { synced: 0, rejected: [], remaining: 0 };

  for (let index = 0; index < entries.length; index++) {
    const entry = entries[index];
    try {
      await replay(entry.operation);
      await removeFromOutbox(entry.seq!);
      report.synced++;
    } catch (error) {
      if (alreadyApplied(entry.operation, error)) {
        await removeFromOutbox(entry.seq!);
        report.synced++;
        continue;
      }

      const kind = classifyFailure(error);
      const reason = getErrorMessage(error, error instanceof Error ? error.message : "Unknown error");
      if (kind === "rejected") {
        await removeFromOutbox(entry.seq!);
        report.rejected.push({ entry, reason });
        continue;
      }

      await recordFailedAttempt(entry, reason);
      report.stoppedBy = kind;
      report.remaining = entries.length - index;
      break;
    }
  }

  return report;
}
