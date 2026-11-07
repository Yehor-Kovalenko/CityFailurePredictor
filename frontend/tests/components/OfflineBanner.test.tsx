import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { OfflineBanner } from "@/components/OfflineBanner";

describe("OfflineBanner", () => {
  it("is hidden when online with live data", () => {
    const { container } = render(<OfflineBanner online cachedAt={null} pending={0} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("explains that saved data is shown and changes will sync", () => {
    render(<OfflineBanner online={false} cachedAt={Date.now() - 5 * 60_000} pending={3} />);

    expect(screen.getByRole("status")).toHaveTextContent(
      "You are offline. Showing data saved 5 minutes ago. 3 changes will sync when the connection returns.",
    );
  });

  it("covers an unreachable server while the device is online", () => {
    render(<OfflineBanner online cachedAt={Date.now()} pending={0} />);

    expect(screen.getByRole("status")).toHaveTextContent("Server unreachable.");
  });
});
