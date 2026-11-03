import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { HeartbeatFeed } from "@/components/HeartbeatFeed";

describe("HeartbeatFeed", () => {
  it("is off-canvas while closed", () => {
    render(<HeartbeatFeed incidents={[]} alerts={[]} />);

    const feed = screen.getByRole("complementary", { hidden: true });
    expect(feed).toHaveClass("translate-x-full", "invisible");
    expect(screen.queryByTestId("heartbeat-feed-backdrop")).not.toBeInTheDocument();
  });

  it("slides in with a backdrop while open", () => {
    render(<HeartbeatFeed incidents={[]} alerts={[]} isOpen onClose={vi.fn()} />);

    expect(screen.getByRole("complementary", { name: "Heartbeat feed" })).toHaveClass("translate-x-0");
    expect(screen.getByTestId("heartbeat-feed-backdrop")).toBeInTheDocument();
  });

  it.each([
    ["close button", async (user: ReturnType<typeof userEvent.setup>) =>
      user.click(screen.getByRole("button", { name: "Close activity feed" }))],
    ["backdrop", async (user: ReturnType<typeof userEvent.setup>) =>
      user.click(screen.getByTestId("heartbeat-feed-backdrop"))],
    ["Escape key", async (user: ReturnType<typeof userEvent.setup>) => user.keyboard("{Escape}")],
  ])("closes via the %s", async (_, act) => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<HeartbeatFeed incidents={[]} alerts={[]} isOpen onClose={onClose} />);

    await act(user);

    expect(onClose).toHaveBeenCalledOnce();
  });

  it("ignores Escape while closed", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<HeartbeatFeed incidents={[]} alerts={[]} onClose={onClose} />);

    await user.keyboard("{Escape}");

    expect(onClose).not.toHaveBeenCalled();
  });
});
