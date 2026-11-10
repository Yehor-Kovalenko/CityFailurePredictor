# City Failure Predictor - Frontend

React 18 + TypeScript + Vite + Tailwind CSS.

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Dev server on http://localhost:3000 (API proxied to `:8080`) |
| `npm run build` | Type check and production build into `dist/` |
| `npm run lint` | ESLint, zero warnings allowed |
| `npm test` / `npm run test:coverage` | Unit and component tests (Vitest, jsdom) |
| `npm run test:e2e` | End-to-end suite: responsive layout and PWA checks (Playwright, Chromium) |
| `npm run test:e2e:ui` | The same suite in Playwright's interactive UI |
| `npm run generate-pwa-assets` | Regenerates the PWA icon set from `public/favicon.svg` |

Before the first e2e run install the browser once: `npx playwright install chromium`.

## Responsive layout

The UI is mobile-first and supports widths from 320 px up:

| Breakpoint | Layout |
|---|---|
| `< md` (768 px) | Navigation is a bottom bar; the search field takes its own row in the top bar; action buttons are icon-only |
| `md` - `xl` | Navigation is a vertical rail on the left |
| `< xl` (1280 px) | The heartbeat feed is an off-canvas drawer opened from the top bar (closes on backdrop click, close button or `Esc`) |
| `>= xl` | The heartbeat feed is docked on the right |

Modals and dropdowns are bounded by the viewport (`dvh` units, safe-area insets for notched devices) and scroll internally.

### How it is verified

`e2e/responsive.spec.ts` runs against the production build (`vite preview`) with every backend call mocked, so no services are needed. For six viewports (320x568, 375x667, 667x375, 768x1024, 1366x768, 1920x1080) it checks that:

- the page never scrolls horizontally and no visible control is cut off on any tab,
- the main controls, the activity feed, the notifications dropdown and the create-incident dialog are reachable.

Full-page screenshots of every tab at every viewport are attached to the HTML report (`playwright-report/`). CI runs the suite in the `frontend-e2e` job and uploads the report as an artifact.

## Splash screen and web app manifest

Two complementary splash screens:

- **In-page splash** - markup and critical CSS are inlined in `index.html`, so it paints on the first frame before any JavaScript loads (in the browser and in the installed app alike). `src/splash.ts` fades it out once the app is ready (session restored); it stays at least 600 ms after navigation start so it does not flash on fast loads, but never adds delay on slow ones. Animations are disabled for `prefers-reduced-motion`.
- **System splash** - when the app is installed, the OS builds a launch screen from `public/manifest.webmanifest` (`name`, `background_color`, 512 px icon).

Icons (64, 192, 512, maskable 512, Apple touch 180, `favicon.ico`) are generated from `public/favicon.svg` by `@vite-pwa/assets-generator` (`pwa-assets.config.ts`) and committed. nginx serves the manifest as `application/manifest+json` with `Cache-Control: no-cache`.

`e2e/pwa.spec.ts` verifies the manifest fields, that every declared icon exists with its declared size, that Chrome reports no installability or manifest errors (via the DevTools Protocol), and that the splash is visible while the bundle loads and removed afterwards.
