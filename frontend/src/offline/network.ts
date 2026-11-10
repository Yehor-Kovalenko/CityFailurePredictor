import { isAxiosError } from "axios";

export function isNetworkError(error: unknown): boolean {
  return isAxiosError(error) && !error.response;
}

export function isBrowserOffline(): boolean {
  return typeof navigator !== "undefined" && navigator.onLine === false;
}

export type FailureKind = "retry" | "auth" | "rejected";

export function classifyFailure(error: unknown): FailureKind {
  if (!isAxiosError(error) || !error.response) {
    return "retry";
  }
  const { status } = error.response;
  if (status === 401) return "auth";
  if (status === 408 || status === 429 || status >= 500) return "retry";
  return "rejected";
}

export function httpStatusOf(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}
