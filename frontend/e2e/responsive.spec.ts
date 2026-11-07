import { expect, test, type Page } from "@playwright/test";
import { bootstrapApp } from "./fixtures";

const VIEWPORTS = [
  { name: "small-phone", width: 320, height: 568 },
  { name: "phone", width: 375, height: 667 },
  { name: "phone-landscape", width: 667, height: 375 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "laptop", width: 1366, height: 768 },
  { name: "desktop", width: 1920, height: 1080 },
] as const;

const TABS = ["Dashboard", "Incidents", "Analytics", "Settings"] as const;

async function expectNothingCutOff(page: Page): Promise<void> {
  const report = await page.evaluate(() => {
    const viewportWidth = document.documentElement.clientWidth;
    const pageOverflow = document.documentElement.scrollWidth - viewportWidth;

    const offenders = Array.from(
      document.querySelectorAll<HTMLElement>("button, a[href], input, select, textarea, h1, h2, h3"),
    )
      .filter((el) => {
        const style = getComputedStyle(el);
        const rect = el.getBoundingClientRect();
        return style.visibility !== "hidden" && rect.width > 0 && rect.height > 0;
      })
      .filter((el) => !el.closest(".leaflet-pane"))
      .filter((el) => {
        const rect = el.getBoundingClientRect();
        return rect.left < -1 || rect.right > viewportWidth + 1;
      })
      .map((el) => el.getAttribute("aria-label") ?? el.textContent?.trim().slice(0, 40) ?? el.tagName);

    return { pageOverflow, offenders };
  });

  expect(report.pageOverflow, "page must not scroll horizontally").toBeLessThanOrEqual(0);
  expect(report.offenders, "controls cut off horizontally").toEqual([]);
}

for (const viewport of VIEWPORTS) {
  test.describe(`${viewport.name} ${viewport.width}x${viewport.height}`, () => {
    test.use({ viewport: { width: viewport.width, height: viewport.height } });

    test("login screen fits", async ({ page }) => {
      await page.goto("/");

      await expect(page.getByRole("button", { name: "Continue with Google" })).toBeInViewport();
      await expectNothingCutOff(page);
    });

    test("every tab fits and the main controls are reachable", async ({ page }, testInfo) => {
      await bootstrapApp(page);
      await page.goto("/");

      const nav = page.getByRole("navigation", { name: "Main navigation" });
      await expect(nav).toBeInViewport();
      await expect(page.getByRole("button", { name: "New Incident" })).toBeInViewport();
      await expect(page.getByRole("button", { name: "Notifications" })).toBeInViewport();
      await expect(page.getByRole("button", { name: "Logout" })).toBeInViewport();
      await expect(page.getByRole("textbox", { name: "Search incidents" })).toBeInViewport();

      for (const tab of TABS) {
        await nav.getByRole("button", { name: tab }).click();
        await expect(nav.getByRole("button", { name: tab })).toHaveAttribute("aria-current", "page");
        await expectNothingCutOff(page);
        await testInfo.attach(`${tab.toLowerCase()}.png`, {
          body: await page.screenshot({ fullPage: true }),
          contentType: "image/png",
        });
      }
    });

    test("activity feed is reachable", async ({ page }) => {
      await bootstrapApp(page);
      await page.goto("/");
      const feed = page.getByRole("complementary", { name: "Heartbeat feed" });

      if (viewport.width >= 1280) {
        // Docked next to the content from the xl breakpoint up.
        await expect(feed).toBeInViewport();
        await expect(page.getByRole("button", { name: "Toggle activity feed" })).toBeHidden();
        return;
      }

      await expect(feed).not.toBeInViewport();
      await page.getByRole("button", { name: "Toggle activity feed" }).click();
      await expect(feed).toBeInViewport({ ratio: 1 });
      await expectNothingCutOff(page);

      await page.keyboard.press("Escape");
      await expect(feed).not.toBeInViewport();
    });

    test("notifications dropdown fits", async ({ page }) => {
      await bootstrapApp(page);
      await page.goto("/");

      await page.getByRole("button", { name: "Notifications" }).click();
      const item = page.getByRole("button", { name: /Critical overload risk predicted/ });
      await expect(item).toBeInViewport({ ratio: 1 });
      await expectNothingCutOff(page);
    });

    test("create incident dialog fits and can be scrolled to submit", async ({ page }) => {
      await bootstrapApp(page);
      await page.goto("/");

      await page.getByRole("button", { name: "New Incident" }).click();
      const dialog = page.getByRole("dialog", { name: "Create New Incident" });
      await expect(dialog).toBeVisible();
      await expectNothingCutOff(page);

      const submit = dialog.getByRole("button", { name: "Create Incident" });
      await submit.scrollIntoViewIfNeeded();
      await expect(submit).toBeInViewport({ ratio: 1 });
      await expect(dialog.getByRole("button", { name: "Close" })).toBeVisible();
    });
  });
}
