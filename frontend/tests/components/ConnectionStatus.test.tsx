import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ConnectionStatus } from "@/components/ConnectionStatus";
import type { SyncState } from "@/offline/syncManager";

const { state, requestSync } = vi.hoisted(() => ({
  state: { current: null as unknown as SyncState },
  requestSync: vi.fn(),
}));

vi.mock("@/offline/syncManager", () => ({
  getSyncState: () => state.current,
  subscribeSyncState: () => () => undefined,
  requestSync,
}));

function renderWith(patch: Partial<SyncState>) {
  state.current = { online: true, pending: 0, syncing: false, lastSyncedAt: null, lastError: null, ...patch };
  return render(<ConnectionStatus />);
}

describe("ConnectionStatus", () => {
  afterEach(() => vi.clearAllMocks());

  it("stays hidden while online with nothing to sync", () => {
    const { container } = renderWith({});

    expect(container).toBeEmptyDOMElement();
  });

  it("shows the offline state and the number of waiting changes", () => {
    renderWith({ online: false, pending: 2 });

    expect(screen.getAllByText("Offline")[0]).toBeInTheDocument();
    const sync = screen.getByRole("button", { name: "2 changes waiting to sync. Sync now" });
    expect(sync).toBeDisabled();
  });

  it("synchronizes on demand when online", async () => {
    const user = userEvent.setup();
    renderWith({ pending: 1 });

    await act(() => user.click(screen.getByRole("button", { name: "1 change waiting to sync. Sync now" })));

    expect(requestSync).toHaveBeenCalledOnce();
  });

  it("shows progress while synchronizing", () => {
    renderWith({ pending: 1, syncing: true });

    expect(screen.getByRole("button", { name: "Synchronizing changes" })).toBeDisabled();
  });
});
