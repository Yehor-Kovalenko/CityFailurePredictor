import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  SPLASH_ELEMENT_ID,
  SPLASH_FADE_MS,
  SPLASH_HIDDEN_CLASS,
  hideSplash,
  resetSplashForTests,
} from "@/splash";

function mountSplash(): HTMLElement {
  const splash = document.createElement("div");
  splash.id = SPLASH_ELEMENT_ID;
  document.body.appendChild(splash);
  return splash;
}

describe("hideSplash", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(performance, "now").mockReturnValue(0);
    resetSplashForTests();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    document.body.innerHTML = "";
  });

  it("keeps the splash for the minimum time, fades it, then removes it", async () => {
    const splash = mountSplash();

    const done = hideSplash(500);

    await vi.advanceTimersByTimeAsync(499);
    expect(splash).not.toHaveClass(SPLASH_HIDDEN_CLASS);

    await vi.advanceTimersByTimeAsync(1);
    expect(splash).toHaveClass(SPLASH_HIDDEN_CLASS);
    expect(document.getElementById(SPLASH_ELEMENT_ID)).not.toBeNull();

    await vi.advanceTimersByTimeAsync(SPLASH_FADE_MS);
    await done;
    expect(document.getElementById(SPLASH_ELEMENT_ID)).toBeNull();
  });

  it("does not add delay when the app took longer than the minimum to load", async () => {
    vi.mocked(performance.now).mockReturnValue(2_000);
    const splash = mountSplash();

    void hideSplash(500);
    await vi.advanceTimersByTimeAsync(0);

    expect(splash).toHaveClass(SPLASH_HIDDEN_CLASS);
  });

  it("removes the splash without a fade when reduced motion is preferred", async () => {
    vi.stubGlobal("matchMedia", (query: string) => ({ matches: query.includes("reduce") }));
    mountSplash();

    void hideSplash(0);
    await vi.advanceTimersByTimeAsync(SPLASH_FADE_MS - 1);

    expect(document.getElementById(SPLASH_ELEMENT_ID)).toBeNull();
    vi.unstubAllGlobals();
  });

  it("shares a single run between repeated calls", () => {
    mountSplash();

    expect(hideSplash(0)).toBe(hideSplash(0));
  });

  it("is a no-op when there is no splash element", async () => {
    await expect(hideSplash(0)).resolves.toBeUndefined();
  });
});
