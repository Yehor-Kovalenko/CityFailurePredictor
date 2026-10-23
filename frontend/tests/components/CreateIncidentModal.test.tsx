import {render, screen, waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {afterEach, describe, expect, it, vi} from "vitest";
import {CreateIncidentModal} from "@/components/CreateIncidentModal";

const { createIncident } = vi.hoisted(() => ({
  createIncident: vi.fn(),
}));

vi.mock("@/services/api", () => ({
  incidentService: { createIncident },
}));

function renderModal(isOpen = true) {
  const handlers = { onClose: vi.fn(), onSuccess: vi.fn() };
  render(<CreateIncidentModal isOpen={isOpen} {...handlers} />);
  return handlers;
}

async function fillForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByPlaceholderText("e.g., Water Main Burst"), "Broken pipe");
  await user.type(screen.getByPlaceholderText("Describe the incident..."), "Water on the street");
  await user.selectOptions(screen.getByRole("combobox"), "WATER");
  await user.type(screen.getByPlaceholderText("51.5074"), "51.7592");
  await user.type(screen.getByPlaceholderText("19.4568"), "19.456");
}

describe("CreateIncidentModal", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("renders nothing when closed", () => {
    renderModal(false);

    expect(screen.queryByRole("heading", { name: "Create New Incident" })).not.toBeInTheDocument();
  });

  it("creates the incident and closes", async () => {
    const user = userEvent.setup();
    createIncident.mockResolvedValue(undefined);
    const { onClose, onSuccess } = renderModal();

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Create Incident" }));

    expect(createIncident).toHaveBeenCalledWith({
      incidentTitle: "Broken pipe",
      incidentSummary: "Water on the street",
      coordinates: { crs: "EPSG:4326", x: "51.7592", y: "19.456" },
      incidentType: "WATER",
    });
    expect(onSuccess).toHaveBeenCalledOnce();
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("shows an error and stays open when creation fails", async () => {
    const user = userEvent.setup();
    createIncident.mockRejectedValue(new Error("Network down"));
    const { onClose, onSuccess } = renderModal();

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "Create Incident" }));

    expect(await screen.findByText("Failed to create incident")).toBeInTheDocument();
    expect(onSuccess).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("fills the location from GPS on request", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("navigator", {
      ...navigator,
      geolocation: {
        getCurrentPosition: (ok: PositionCallback) =>
          ok({ coords: { latitude: 51.7592341, longitude: 19.4560123 } } as GeolocationPosition),
      },
    });
    renderModal();

    await user.click(screen.getByRole("button", { name: "Use my location" }));

    expect(screen.getByPlaceholderText("51.5074")).toHaveValue(51.759234);
    expect(screen.getByPlaceholderText("19.4568")).toHaveValue(19.456012);
    vi.unstubAllGlobals();
  });

  it("auto-fills the location when the user has already consented", async () => {
    localStorage.setItem("city-failure-predictor.geo-consent", "granted");
    vi.stubGlobal("navigator", {
      ...navigator,
      geolocation: {
        getCurrentPosition: (ok: PositionCallback) =>
          ok({ coords: { latitude: 51.75, longitude: 19.45 } } as GeolocationPosition),
      },
    });
    renderModal();

    await waitFor(() => expect(screen.getByPlaceholderText("51.5074")).toHaveValue(51.75));
    vi.unstubAllGlobals();
  });

  it("shows a message when the location cannot be read", async () => {
    const user = userEvent.setup();
    renderModal();

    await user.click(screen.getByRole("button", { name: "Use my location" }));

    expect(await screen.findByText(/not supported/)).toBeInTheDocument();
  });

  it("closes on cancel", async () => {
    const user = userEvent.setup();
    const { onClose } = renderModal();

    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(onClose).toHaveBeenCalledOnce();
  });
});
