export function supportsAppBadge(): boolean {
  return "setAppBadge" in navigator;
}

export async function updateAppBadge(unreadCount: number): Promise<void> {
  if (!supportsAppBadge()) {
    return;
  }

  try {
    if (unreadCount > 0) {
      await navigator.setAppBadge(unreadCount);
    } else {
      await navigator.clearAppBadge();
    }
  } catch (error) {
    console.warn("App badge is not available:", error);
  }
}
