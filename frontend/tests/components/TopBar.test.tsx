import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { TopBar } from "@/components/TopBar";

function renderTopBar(overrides: Partial<Parameters<typeof TopBar>[0]> = {}) {
  const props = {
    searchQuery: "",
    onSearchChange: vi.fn(),
    onNotificationsClick: vi.fn(),
    onCreateClick: vi.fn(),
    onLogout: vi.fn(),
    userEmail: "jane.doe@example.com",
    userRole: "ROLE_USER" as const,
    ...overrides,
  };
  render(<TopBar {...props} />);
  return props;
}

describe("TopBar", () => {
  it("keeps icon-only actions accessible by name", async () => {
    const user = userEvent.setup();
    const props = renderTopBar();

    await user.click(screen.getByRole("button", { name: "New Incident" }));
    await user.click(screen.getByRole("button", { name: "Notifications" }));
    await user.click(screen.getByRole("button", { name: "Logout" }));

    expect(props.onCreateClick).toHaveBeenCalledOnce();
    expect(props.onNotificationsClick).toHaveBeenCalledOnce();
    expect(props.onLogout).toHaveBeenCalledOnce();
  });

  it("forwards search input", async () => {
    const user = userEvent.setup();
    const props = renderTopBar();

    await user.type(screen.getByRole("textbox", { name: "Search incidents" }), "a");

    expect(props.onSearchChange).toHaveBeenCalledWith("a");
  });

  it("renders the feed toggle only when a handler is provided", async () => {
    const user = userEvent.setup();
    const onFeedToggle = vi.fn();
    renderTopBar({ onFeedToggle, isFeedOpen: true });
    const toggle = screen.getByRole("button", { name: "Toggle activity feed" });
    expect(toggle).toHaveAttribute("aria-expanded", "true");

    await user.click(toggle);
    expect(onFeedToggle).toHaveBeenCalledOnce();
  });

  it("omits the feed toggle without a handler", () => {
    renderTopBar();

    expect(screen.queryByRole("button", { name: "Toggle activity feed" })).not.toBeInTheDocument();
  });
});
