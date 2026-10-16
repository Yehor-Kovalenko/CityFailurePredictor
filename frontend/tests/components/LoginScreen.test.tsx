import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { LoginScreen } from "@/components/LoginScreen";

describe("LoginScreen", () => {
  it("starts the Google login on click", async () => {
    const user = userEvent.setup();
    const onLogin = vi.fn().mockResolvedValue(undefined);
    render(<LoginScreen onLogin={onLogin} loading={false} error={null} />);

    await user.click(screen.getByRole("button", { name: "Continue with Google" }));

    expect(onLogin).toHaveBeenCalledOnce();
  });

  it("disables the button while signing in", () => {
    render(<LoginScreen onLogin={vi.fn()} loading error={null} />);

    expect(screen.getByRole("button", { name: "Signing in..." })).toBeDisabled();
  });

  it("shows the login error", () => {
    render(<LoginScreen onLogin={vi.fn()} loading={false} error="Popup blocked" />);

    expect(screen.getByText("Popup blocked")).toBeInTheDocument();
  });
});
