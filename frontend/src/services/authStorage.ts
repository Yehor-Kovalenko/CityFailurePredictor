import { AuthResponse, AuthSession } from "../types";

const AUTH_STORAGE_KEY = "city-failure-predictor.auth";
const AUTH_CHANGED_EVENT = "auth:session-changed";

function notifyAuthChanged(): void {
  window.dispatchEvent(new Event(AUTH_CHANGED_EVENT));
}

export function toSession(response: AuthResponse): AuthSession {
  return {
    accessToken: response.accessToken,
    tokenType: response.tokenType,
    expiresAt: Date.now() + response.expiresIn * 1000,
    user: response.user,
  };
}

export function getStoredSession(): AuthSession | null {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as AuthSession;
    if (!parsed.accessToken || !parsed.expiresAt || !parsed.user?.email) {
      clearStoredSession();
      return null;
    }

    if (parsed.expiresAt <= Date.now()) {
      clearStoredSession();
      return null;
    }

    return parsed;
  } catch {
    clearStoredSession();
    return null;
  }
}

export function saveStoredSession(session: AuthSession): void {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
  notifyAuthChanged();
}

export function clearStoredSession(): void {
  localStorage.removeItem(AUTH_STORAGE_KEY);
  notifyAuthChanged();
}

export function getAuthChangedEventName(): string {
  return AUTH_CHANGED_EVENT;
}
