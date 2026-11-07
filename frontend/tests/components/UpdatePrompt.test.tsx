import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { UpdatePrompt } from "@/components/UpdatePrompt";
import type { SyncState } from "@/offline/syncManager";

const mocks = vi.hoisted(() => ({
  needRefresh: false,
  offlineReady: false,
  setNeedRefresh: vi.fn(),
  setOfflineReady: vi.fn(),
  updateServiceWorker: vi.fn(),
  requestSync: vi.fn(),
  sync: { online: true, pending: 0 } as Partial<SyncState>,
}));

vi.mock("virtual:pwa-register/react", () => ({
  useRegisterSW: () => ({
    needRefresh: [mocks.needRefresh, mocks.setNeedRefresh],
    offlineReady: [mocks.offlineReady, mocks.setOfflineReady],
    updateServiceWorker: mocks.updateServiceWorker,
  }),
}));

vi.mock("@/hooks/useSyncStatus", () => ({ useSyncStatus: () => mocks.sync }));
vi.mock("@/offline/syncManager", () => ({ requestSync: mocks.requestSync }));

describe("UpdatePrompt", () => {
  afterEach(() => {
    vi.clearAllMocks();
    Object.assign(mocks, { needRefresh: false, offlineReady: false, sync: { online: true, pending: 0 } });
  });

  it("renders nothing without a pending update", () => {
    const { container } = render(<UpdatePrompt />);

    expect(container).toBeEmptyDOMElement();
  });

  it("tells the user the app works offline once cached", () => {
    mocks.offlineReady = true;
    render(<UpdatePrompt />);

    expect(screen.getByRole("alertdialog", { name: "Ready to work offline" })).toBeInTheDocument();
  });

  it("lets the user postpone an update", async () => {
    const user = userEvent.setup();
    mocks.needRefresh = true;
    render(<UpdatePrompt />);

    await user.click(screen.getByRole("button", { name: "Later" }));

    expect(mocks.setNeedRefresh).toHaveBeenCalledWith(false);
    expect(mocks.updateServiceWorker).not.toHaveBeenCalled();
  });

  it("syncs pending changes before activating the new version", async () => {
    const user = userEvent.setup();
    const order: string[] = [];
    mocks.requestSync.mockImplementation(async () => order.push("sync"));
    mocks.updateServiceWorker.mockImplementation(async () => order.push("update"));
    mocks.needRefresh = true;
    mocks.sync = { online: true, pending: 2 };
    render(<UpdatePrompt />);

    expect(screen.getByText(/Your 2 unsynced changes are kept on this device/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Reload" }));

    expect(order).toEqual(["sync", "update"]);
    expect(mocks.updateServiceWorker).toHaveBeenCalledWith(true);
  });

  it("updates without waiting for a sync while offline; changes stay queued", async () => {
    const user = userEvent.setup();
    mocks.needRefresh = true;
    mocks.sync = { online: false, pending: 1 };
    render(<UpdatePrompt />);

    await user.click(screen.getByRole("button", { name: "Reload" }));

    expect(mocks.requestSync).not.toHaveBeenCalled();
    expect(mocks.updateServiceWorker).toHaveBeenCalledWith(true);
  });
});
