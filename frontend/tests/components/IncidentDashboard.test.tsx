import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Incident } from "@/types";
import { IncidentDashboard } from "@/components/IncidentDashboard";

const { getAllIncidents } = vi.hoisted(() => ({
  getAllIncidents: vi.fn(),
}));

vi.mock("@/services/api", () => ({
  incidentService: { getAllIncidents },
}));

function incident(id: string, status: Incident["status"]): Incident {
  return {
    id,
    incidentTitle: `Incident ${id}`,
    incidentSummary: "Summary",
    coordinates: { crs: "EPSG:4326", x: "51.7592", y: "19.4560" },
    incidentType: "WATER",
    status,
    timestamp: "2026-01-15T10:00:00Z",
    lastUpdated: "2026-01-15T11:00:00Z",
  };
}

describe("IncidentDashboard", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows loaded incidents and their status counts", async () => {
    getAllIncidents.mockResolvedValue([
      incident("1", "OPEN"),
      incident("2", "OPEN"),
      incident("3", "RESOLVED"),
    ]);
    render(<IncidentDashboard />);

    expect(await screen.findByRole("heading", { name: "Incident 1" })).toBeInTheDocument();
    expect(screen.getByText("Total Incidents").nextSibling).toHaveTextContent("3");
    expect(screen.getByText("Open").nextSibling).toHaveTextContent("2");
    expect(screen.getByText("In Progress").nextSibling).toHaveTextContent("0");
    expect(screen.getByText("Resolved").nextSibling).toHaveTextContent("1");
  });

  it("shows an empty state when there are no incidents", async () => {
    getAllIncidents.mockResolvedValue([]);
    render(<IncidentDashboard />);

    expect(await screen.findByText("No incidents found")).toBeInTheDocument();
  });

  it("shows an error when loading fails", async () => {
    getAllIncidents.mockRejectedValue(new Error("Network down"));
    render(<IncidentDashboard />);

    expect(await screen.findByText("Failed to load incidents")).toBeInTheDocument();
  });

  it("reloads incidents with the selected filters", async () => {
    const user = userEvent.setup();
    getAllIncidents.mockResolvedValue([]);
    render(<IncidentDashboard />);
    await screen.findByText("No incidents found");

    const [statusSelect] = screen.getAllByRole("combobox");
    await user.selectOptions(statusSelect, "OPEN");

    expect(getAllIncidents).toHaveBeenLastCalledWith("OPEN", undefined);
  });

  it("opens the create incident modal", async () => {
    const user = userEvent.setup();
    getAllIncidents.mockResolvedValue([]);
    render(<IncidentDashboard />);

    await user.click(screen.getByRole("button", { name: "Create New Incident" }));

    expect(screen.getByRole("heading", { name: "Create New Incident" })).toBeInTheDocument();
  });
});
