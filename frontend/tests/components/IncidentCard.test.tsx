import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Incident } from "@/types";
import { IncidentCard } from "@/components/IncidentCard";

const { updateStatus, remove } = vi.hoisted(() => ({
  updateStatus: vi.fn(),
  remove: vi.fn(),
}));

vi.mock("@/offline/repository", () => ({
  incidentRepository: { updateStatus, remove },
}));

const incident: Incident = {
  id: "incident-1",
  incidentTitle: "Power Outage",
  incidentSummary: "Transformer failure in the city centre district",
  coordinates: { crs: "EPSG:4326", x: "51.7592", y: "19.4560" },
  incidentType: "ELECTRICITY",
  status: "OPEN",
  timestamp: "2026-01-15T10:00:00Z",
  lastUpdated: "2026-01-15T11:00:00Z",
};

describe("IncidentCard", () => {
  beforeEach(() => {
    updateStatus.mockResolvedValue({ data: { ...incident, status: "RESOLVED" }, queued: false });
    remove.mockResolvedValue({ data: undefined, queued: false });
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it("shows the incident summary", () => {
    render(<IncidentCard incident={incident} onUpdate={vi.fn()} />);

    expect(screen.getByRole("heading", { name: "Power Outage" })).toBeInTheDocument();
    expect(screen.getByText("Transformer failure in the ...")).toBeInTheDocument();
    expect(screen.getByText("51.7592, 19.4560")).toBeInTheDocument();
  });

  it("toggles the details section", async () => {
    const user = userEvent.setup();
    render(<IncidentCard incident={incident} onUpdate={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Show Details" }));
    expect(screen.getByText("incident-1")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Hide Details" }));
    expect(screen.queryByText("incident-1")).not.toBeInTheDocument();
  });

  it("updates the status and refreshes the list", async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    render(<IncidentCard incident={incident} onUpdate={onUpdate} />);

    await user.click(screen.getByRole("button", { name: "Set status RESOLVED" }));

    expect(updateStatus).toHaveBeenCalledWith(incident, "RESOLVED");
    expect(onUpdate).toHaveBeenCalledOnce();
  });

  it("does not call the API when the current status is selected", async () => {
    const user = userEvent.setup();
    render(<IncidentCard incident={incident} onUpdate={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Set status OPEN" }));

    expect(updateStatus).not.toHaveBeenCalled();
  });

  it("deletes the incident only after confirmation", async () => {
    const user = userEvent.setup();
    const onUpdate = vi.fn();
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    render(<IncidentCard incident={incident} onUpdate={onUpdate} />);

    await user.click(screen.getByRole("button", { name: "Delete incident" }));
    expect(remove).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Delete incident" }));
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(remove).toHaveBeenCalledWith("incident-1");
    expect(onUpdate).toHaveBeenCalledOnce();
  });

  it("marks incidents with unsynchronized changes", () => {
    render(<IncidentCard incident={{ ...incident, pendingSync: true }} onUpdate={vi.fn()} />);

    expect(screen.getByText("Pending sync")).toBeInTheDocument();
  });

  it("locks status changes of an incident created offline until it is synchronized", () => {
    render(
      <IncidentCard
        incident={{ ...incident, id: "local-123", pendingSync: true }}
        onUpdate={vi.fn()}
      />,
    );

    for (const status of ["OPEN", "IN_PROGRESS", "RESOLVED"]) {
      expect(screen.getByRole("button", { name: `Set status ${status}` })).toBeDisabled();
    }
    expect(screen.getByRole("button", { name: "Delete incident" })).toBeEnabled();
  });
});
