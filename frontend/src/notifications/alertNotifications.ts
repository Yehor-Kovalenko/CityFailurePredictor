import { notificationService } from "@/services/api";

export const OPEN_NOTIFICATIONS_PARAM = "notifications";

export function supportsPush(): boolean {
  return (
    typeof window !== "undefined" &&
    "Notification" in window &&
    "serviceWorker" in navigator &&
    "PushManager" in window
  );
}

function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function sameKey(current: ArrayBuffer | null, expected: Uint8Array): boolean {
  if (!current || current.byteLength !== expected.length) {
    return false;
  }
  const bytes = new Uint8Array(current);
  return expected.every((byte, index) => bytes[index] === byte);
}

export async function syncPushSubscription(): Promise<void> {
  if (!supportsPush() || Notification.permission !== "granted") {
    return;
  }

  const registration = await navigator.serviceWorker.getRegistration();
  const publicKey = registration && (await notificationService.getPushPublicKey());
  if (!registration || !publicKey) {
    return;
  }

  const applicationServerKey = base64UrlToBytes(publicKey);
  let subscription = await registration.pushManager.getSubscription();
  if (subscription && !sameKey(subscription.options.applicationServerKey, applicationServerKey)) {
    await subscription.unsubscribe();
    subscription = null;
  }

  subscription ??= await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey,
  });
  await notificationService.savePushSubscription(subscription.toJSON());
}

export async function enablePush(): Promise<NotificationPermission> {
  const permission = await Notification.requestPermission();
  if (permission === "granted") {
    await syncPushSubscription();
  }
  return permission;
}

export async function disablePush(): Promise<void> {
  const registration = await navigator.serviceWorker?.getRegistration();
  const subscription = await registration?.pushManager?.getSubscription();
  if (!subscription) {
    return;
  }

  try {
    await notificationService.deletePushSubscription(subscription.endpoint);
  } finally {
    await subscription.unsubscribe();
  }
}
