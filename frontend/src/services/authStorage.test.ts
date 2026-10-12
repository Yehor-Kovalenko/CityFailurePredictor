import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthResponse, AuthSession } from "../types";
import {
  clearStoredSession,
  getAuthChangedEventName,
  getStoredSession,
  saveStoredSession,
  toSession,
} from "./authStorage";

const STORAGE_KEY = "city-failure-predictor.auth";
const NOW = new Date("2026-01-15T12:00:00Z").getTime();

function session(overrides: Partial<AuthSession> = {}): AuthSession {
  return {
    accessToken: "token",
    tokenType: "Bearer",
    expiresAt: NOW + 60_000,
    user: { email: "user@example.com", role: "ROLE_USER" },
    ...overrides,
  };
}

describe("authStorage", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("converts an auth response into a session with an absolute expiry", () => {
    const response: AuthResponse = {
      accessToken: "token",
      tokenType: "Bearer",
      expiresIn: 3600,
      user: { email: "user@example.com", role: "ROLE_ADMIN" },
    };

    expect(toSession(response)).toEqual({
      accessToken: "token",
      tokenType: "Bearer",
      expiresAt: NOW + 3600 * 1000,
      user: response.user,
    });
  });

  it("returns null when nothing is stored", () => {
    expect(getStoredSession()).toBeNull();
  });

  it("round-trips a valid session and notifies listeners", () => {
    const listener = vi.fn();
    window.addEventListener(getAuthChangedEventName(), listener);

    saveStoredSession(session());

    expect(getStoredSession()).toEqual(session());
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener(getAuthChangedEventName(), listener);
  });

  it("drops an expired session", () => {
    saveStoredSession(session({ expiresAt: NOW - 1 }));

    expect(getStoredSession()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("drops an incomplete session", () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ accessToken: "token" }));

    expect(getStoredSession()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("drops a corrupted session", () => {
    localStorage.setItem(STORAGE_KEY, "{not json");

    expect(getStoredSession()).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it("clears the stored session and notifies listeners", () => {
    saveStoredSession(session());
    const listener = vi.fn();
    window.addEventListener(getAuthChangedEventName(), listener);

    clearStoredSession();

    expect(getStoredSession()).toBeNull();
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener(getAuthChangedEventName(), listener);
  });
});
