export const SPLASH_ELEMENT_ID = "splash";
export const SPLASH_HIDDEN_CLASS = "splash--hidden";
export const SPLASH_MIN_VISIBLE_MS = 600;
export const SPLASH_FADE_MS = 300;

let hiding: Promise<void> | null = null;

function prefersReducedMotion(): boolean {
  return window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, Math.max(0, ms)));
}

export function hideSplash(minVisibleMs: number = SPLASH_MIN_VISIBLE_MS): Promise<void> {
  if (hiding) {
    return hiding;
  }

  const splash = document.getElementById(SPLASH_ELEMENT_ID);
  if (!splash) {
    return Promise.resolve();
  }

  hiding = (async () => {
    await wait(minVisibleMs - performance.now());
    splash.classList.add(SPLASH_HIDDEN_CLASS);
    splash.setAttribute("aria-busy", "false");
    await wait(prefersReducedMotion() ? 0 : SPLASH_FADE_MS);
    splash.remove();
  })();

  return hiding;
}

export function resetSplashForTests(): void {
  hiding = null;
}
