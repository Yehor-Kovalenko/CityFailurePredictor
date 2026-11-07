import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Sidebar } from "@/components/Sidebar";

describe("Sidebar", () => {
  it("exposes an accessible navigation with the active tab marked", () => {
    render(<Sidebar activeTab="incidents" onTabChange={vi.fn()} canAccessSettings />);

    expect(screen.getByRole("navigation", { name: "Main navigation" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Incidents" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Dashboard" })).not.toHaveAttribute("aria-current");
  });

  it("switches tabs on click", async () => {
    const user = userEvent.setup();
    const onTabChange = vi.fn();
    render(<Sidebar activeTab="dashboard" onTabChange={onTabChange} canAccessSettings />);

    await user.click(screen.getByRole("button", { name: "Analytics" }));

    expect(onTabChange).toHaveBeenCalledWith("analytics");
  });

  it("disables settings for non-admins", () => {
    render(<Sidebar activeTab="dashboard" onTabChange={vi.fn()} canAccessSettings={false} />);

    expect(screen.getByRole("button", { name: "Settings" })).toBeDisabled();
  });
});
