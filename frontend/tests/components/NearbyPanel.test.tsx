import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { NearbyPanel } from "@/components/NearbyPanel";

function renderPanel(props: Partial<Parameters<typeof NearbyPanel>[0]> = {}) {
  const handlers = {
    onRadiusChange: vi.fn(),
    onGrant: vi.fn(),
    onDeny: vi.fn(),
    onClearArea: vi.fn(),
  };
  render(
    <NearbyPanel
      consent="unknown"
      hasArea={false}
      visibleCount={0}
      radiusKm={3}
      error={null}
      {...handlers}
      {...props}
    />,
  );
  return handlers;
}

describe("NearbyPanel", () => {
  it("asks for consent first", async () => {
    const user = userEvent.setup();
    const { onGrant, onDeny } = renderPanel();

    await user.click(screen.getByRole("button", { name: "Use my location" }));
    await user.click(screen.getByRole("button", { name: "Not now" }));

    expect(onGrant).toHaveBeenCalledOnce();
    expect(onDeny).toHaveBeenCalledOnce();
    expect(screen.queryByLabelText("Radius")).not.toBeInTheDocument();
  });

  it("lets a consenting user change the radius and opt out", async () => {
    const user = userEvent.setup();
    const { onRadiusChange, onDeny } = renderPanel({ consent: "granted", hasArea: true, visibleCount: 2 });

    await user.selectOptions(screen.getByLabelText("Radius"), "10");
    await user.click(screen.getByRole("button", { name: "Stop using location" }));

    expect(screen.getByText("2 incidents in range")).toBeInTheDocument();
    expect(onRadiusChange).toHaveBeenCalledWith(10);
    expect(onDeny).toHaveBeenCalledOnce();
  });

  it("offers manual area selection after refusal", async () => {
    const user = userEvent.setup();
    const { onClearArea } = renderPanel({ consent: "denied", hasArea: true, visibleCount: 1 });

    await user.click(screen.getByRole("button", { name: "Clear area" }));

    expect(screen.getByText("Incidents in the selected area")).toBeInTheDocument();
    expect(screen.getByText("1 incident in range")).toBeInTheDocument();
    expect(onClearArea).toHaveBeenCalledOnce();
  });
});
