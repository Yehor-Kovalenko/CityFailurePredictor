# City Failure Predictor - Frontend

React 18 + TypeScript + Vite + Tailwind CSS.

## Scripts

| Command | Purpose |
|---|---|
| `npm run dev` | Dev server on http://localhost:3000 (API proxied to `:8080`) |
| `npm run build` | Type check and production build into `dist/` |
| `npm run lint` | ESLint, zero warnings allowed |
| `npm test` / `npm run test:coverage` | Unit and component tests (Vitest, jsdom) |
| `npm run test:e2e` | End-to-end suite: responsive layout, PWA, offline and update checks (Playwright, Chromium) |
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

## Offline mode and synchronization

The app keeps working without a connection; changes made offline are sent automatically when it returns.

| Layer | Where | What it does |
|---|---|---|
| App shell | Service worker (`vite-plugin-pwa`, Workbox `generateSW`) | Precaches HTML, JS, CSS, icons and the manifest, so the app starts offline. Map tiles (`map-tiles`, stale-while-revalidate, 500 tiles / 7 days) and Leaflet marker images (`leaflet-assets`) are cached at runtime |
| Server data | IndexedDB `snapshots` store (`src/offline/snapshots.ts`) | Network first: every successful load of incidents and notifications is saved; when the server is unreachable the last copy is shown with a banner saying how old it is. Scoped per user and cleared on logout |
| Changes | IndexedDB `outbox` store (`src/offline/outbox.ts`) | Creating an incident, changing its status, deleting it and marking a notification as read are queued when the device is offline, the request fails for lack of a connection, or older changes are still queued (keeps server order). The UI shows them immediately with a "Pending sync" badge and a pending counter in the top bar |
| Sync | `src/offline/sync.ts`, `src/offline/syncManager.ts` | Replays the queue oldest first on start-up, on reconnect, when the tab becomes visible, after each new change and every 30 s while changes are pending. Stops at the first transient failure (no connection, 408, 429, 5xx) to keep the order; keeps changes on 401 until the user signs in again; drops and reports changes the server refuses (other 4xx). A 404 on delete or mark-as-read counts as done. A Web Lock ensures only one tab syncs at a time; a `BroadcastChannel` keeps the counters of all tabs up to date |

The outbox coalesces redundant changes: a newer status change replaces an older one, deleting an incident drops its queued status changes, and deleting an incident created offline cancels its creation. An incident created offline has a temporary `local-` id; its status can change once it is synchronized.

Limitation: the backend has no idempotency keys, so if the connection drops after the server stored a new incident but before the response arrives, the replay creates it a second time.

## Updates

A new release must never lose unsynchronized changes or cached data:

- **User-controlled activation.** The service worker uses `registerType: "prompt"`: a new version installs in the background and waits. `UpdatePrompt` offers "Reload" or "Later" and tells the user that their unsynced changes are kept. Before reloading it tries to sync (when online). The app checks for a new version every hour while open.
- **Data outside the versioned caches.** The outbox and snapshots live in IndexedDB, which updates do not touch. Its schema evolves through append-only migrations (`MIGRATIONS` in `src/offline/db.ts`): a released migration is never changed and no migration drops a store with user data. When another tab runs a newer schema, the old connection closes itself instead of blocking the upgrade.
- **Stable runtime cache names.** Workbox `cleanupOutdatedCaches` removes only precaches of previous builds; `map-tiles` and `leaflet-assets` keep their names, so the new version reuses them.
- **Server headers** (`nginx.conf`). `index.html` (including the SPA fallback), `sw.js` and the manifest are `no-cache`, so clients always see a new release. Hashed files in `/assets/` and `workbox-*.js` are `immutable` for a year; icons are cached for 7 days.

### How it is verified

- Unit tests (`tests/offline/`) use `fake-indexeddb` and cover the schema upgrade keeping data, outbox ordering and coalescing, retry, auth and reject handling during sync, the network-first fallback, and sync triggers.
- `e2e/offline.spec.ts` runs in Chromium against the production build:
  - the app starts offline from the service worker cache and shows the saved incidents;
  - an incident created and another deleted offline are queued and reach the server when the connection returns;
  - an update is simulated by `e2e/releaseServer.ts`, which serves `dist/` and can change `sw.js` like a new deployment. The test queues a change, deploys a new release, accepts the update prompt, and checks that the new version runs with the queued change and cached incidents intact and still syncs them.

## Alert notifications

New infrastructure alerts reach the user as system notifications through Web Push, also when the app is closed.

```
alert (Kafka) -> notification-service -> push service (FCM / Mozilla / Apple / WNS) -> service worker -> system notification
```

- The "Enable alerts" button in the notifications panel asks for permission, subscribes with the server's VAPID public key (`GET /notifications/push/public-key`) and stores the subscription (`POST /notifications/push/subscriptions`). On every start the app re-sends its subscription and replaces it if the server key changed. Logging out removes it (`DELETE /notifications/push/subscriptions`).
- `public/notification-sw.js` is loaded into the Workbox service worker via `importScripts`. It shows the notification (critical alerts stay until dismissed, low ones are silent, repeats of the same alert replace each other), adds the alert to the activity feed of open windows, and on click focuses the app with the notifications panel open or starts it at `/?notifications`.
- The button is hidden when the browser has no Push API; a blocked permission is explained instead.

Server side (`services/notification-service`, package `push`): payload encryption (RFC 8291, `aes128gcm`) and VAPID signing (RFC 8292) use the JDK only. Messages are sent asynchronously after the notification is committed, severity maps to the `Urgency` header, and subscriptions reported as gone (404 / 410) are deleted. Only https endpoints of known push services are accepted. Metric: `notifications_push_deliveries_total{result}`.
