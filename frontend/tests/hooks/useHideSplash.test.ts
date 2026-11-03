import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useHideSplash } from "@/hooks/useHideSplash";
import { hideSplash } from "@/splash";

vi.mock("@/splash", () => ({ hideSplash: vi.fn().mockResolvedValue(undefined) }));

describe("useHideSplash", () => {
  afterEach(() => vi.clearAllMocks());

  it("waits until the app is ready", () => {
    const { rerender } = renderHook(({ ready }) => useHideSplash(ready), {
      initialProps: { ready: false },
    });
    expect(hideSplash).not.toHaveBeenCalled();

    rerender({ ready: true });
    expect(hideSplash).toHaveBeenCalledOnce();
  });
});
