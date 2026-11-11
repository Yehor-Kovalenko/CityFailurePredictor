const ALERT_URL = "/?notifications";

function readAlert(event) {
  try {
    return event.data ? event.data.json() : {};
  } catch {
    return { body: event.data.text() };
  }
}

self.addEventListener("push", (event) => {
  const alert = readAlert(event);
  const title = alert.title || "City Failure Predictor alert";

  event.waitUntil(
    Promise.all([
      self.registration.showNotification(title, {
        body: alert.body,
        tag: alert.alertId ? `alert-${alert.alertId}` : undefined,
        icon: "/pwa-192x192.png",
        badge: "/pwa-64x64.png",
        requireInteraction: alert.severity === "CRITICAL",
        silent: alert.severity === "LOW",
        timestamp: Date.now(),
        data: { url: ALERT_URL },
      }),
      self.clients
        .matchAll({ type: "window" })
        .then((windows) =>
          windows.forEach((client) =>
            client.postMessage({ type: "ALERT_RECEIVED", title, body: alert.body }),
          ),
        ),
    ]),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const target = new URL(event.notification.data?.url ?? "/", self.location.origin).href;

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      const open = windows.find((client) => new URL(client.url).origin === self.location.origin);
      if (open) {
        open.postMessage({ type: "OPEN_NOTIFICATIONS" });
        return open.focus();
      }
      return self.clients.openWindow(target);
    }),
  );
});
