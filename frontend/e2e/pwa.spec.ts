import { expect, test, type APIRequestContext } from "@playwright/test";

interface ManifestIcon {
  src: string;
  sizes: string;
  type: string;
  purpose?: string;
}

interface WebAppManifest {
  name: string;
  short_name: string;
  start_url: string;
  display: string;
  background_color: string;
  theme_color: string;
  icons: ManifestIcon[];
}

function pngSize(png: Buffer): string {
  expect(png.subarray(1, 4).toString("ascii"), "PNG signature").toBe("PNG");
  return `${png.readUInt32BE(16)}x${png.readUInt32BE(20)}`;
}

async function fetchManifest(request: APIRequestContext): Promise<WebAppManifest> {
  const response = await request.get("/manifest.webmanifest");
  expect(response.ok()).toBe(true);
  return (await response.json()) as WebAppManifest;
}

test.describe("web app manifest", () => {
  test("is linked from the document", async ({ page }) => {
    await page.goto("/");

    await expect(page.locator('link[rel="manifest"]')).toHaveAttribute("href", "/manifest.webmanifest");
  });

  test("has the fields the browser needs to build a splash screen and install the app", async ({ request }) => {
    const manifest = await fetchManifest(request);

    expect(manifest.name).toBe("City Failure Predictor");
    expect(manifest.short_name.length).toBeLessThanOrEqual(12);
    expect(manifest.start_url).toBe("/");
    expect(["standalone", "fullscreen", "minimal-ui"]).toContain(manifest.display);
    expect(manifest.background_color).toMatch(/^#[0-9a-f]{6}$/i);
    expect(manifest.theme_color).toMatch(/^#[0-9a-f]{6}$/i);
  });

  test("theme colour matches the document", async ({ page, request }) => {
    const manifest = await fetchManifest(request);
    await page.goto("/");

    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute("content", manifest.theme_color);
  });

  test("every icon exists with the declared size, including 192, 512 and maskable", async ({ request }) => {
    const manifest = await fetchManifest(request);

    for (const icon of manifest.icons) {
      const response = await request.get(icon.src);
      expect(response.ok(), icon.src).toBe(true);
      expect(response.headers()["content-type"]).toContain(icon.type);
      expect(pngSize(await response.body()), icon.src).toBe(icon.sizes);
    }

    const sizes = manifest.icons.map((icon) => icon.sizes);
    expect(sizes).toEqual(expect.arrayContaining(["192x192", "512x512"]));
    expect(manifest.icons.some((icon) => icon.purpose?.includes("maskable"))).toBe(true);
  });

  test("Chrome reports the app as installable with a valid manifest", async ({ page, browserName }) => {
    test.skip(browserName !== "chromium", "Uses the Chrome DevTools Protocol");
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    const cdp = await page.context().newCDPSession(page);
    const { installabilityErrors } = await cdp.send("Page.getInstallabilityErrors");
    const { errors: manifestErrors } = await cdp.send("Page.getAppManifest");

    expect(installabilityErrors).toEqual([]);
    expect(manifestErrors).toEqual([]);
  });

  test("apple touch icon is available", async ({ page, request }) => {
    await page.goto("/");
    const href = await page.locator('link[rel="apple-touch-icon"]').getAttribute("href");

    const response = await request.get(href!);
    expect(pngSize(await response.body())).toBe("180x180");
  });
});

test.describe("splash screen", () => {
  test("shows while the app loads, then gets out of the way", async ({ page }) => {
    // Hold the bundle back so the splash state is observable.
    let releaseBundle!: () => void;
    const bundleReleased = new Promise<void>((resolve) => (releaseBundle = resolve));
    await page.route(/\/assets\/.*\.js$/, async (route) => {
      await bundleReleased;
      await route.continue();
    });

    await page.goto("/", { waitUntil: "commit" });
    const splash = page.getByRole("status", { name: "Loading City Failure Predictor" });
    await expect(splash).toBeVisible();
    await expect(splash.getByRole("heading", { name: "City Failure Predictor" })).toBeVisible();
    await expect(splash).toBeInViewport({ ratio: 1 });

    releaseBundle();

    await expect(page.getByRole("button", { name: "Continue with Google" })).toBeVisible();
    await expect(page.locator("#splash")).toHaveCount(0);
  });

  test("covers the whole viewport on a small phone", async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 568 });
    await page.route(/\/assets\/.*\.js$/, () => {
      // Never load the bundle: the splash must stand on its own.
    });

    await page.goto("/", { waitUntil: "commit" });
    const box = await page.locator("#splash").boundingBox();

    expect(box).toEqual({ x: 0, y: 0, width: 320, height: 568 });
  });

  test("is not announced as busy after the app is ready", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("button", { name: "Continue with Google" })).toBeVisible();
    await expect(page.getByRole("status", { name: "Loading City Failure Predictor" })).toHaveCount(0);
  });
});
