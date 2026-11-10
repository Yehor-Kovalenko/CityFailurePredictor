import { AxiosError, type AxiosResponse } from "axios";
import { IDBFactory } from "fake-indexeddb";
import { closeOfflineDbForTests } from "@/offline/db";
import { saveStoredSession } from "@/services/authStorage";

export const OWNER = "operator@example.com";

export async function freshIndexedDb(): Promise<void> {
  await closeOfflineDbForTests();
  globalThis.indexedDB = new IDBFactory();
}

export function signIn(email = OWNER): void {
  saveStoredSession({
    accessToken: "token",
    tokenType: "Bearer",
    expiresAt: Date.now() + 3_600_000,
    user: { email, role: "ROLE_USER" },
  });
}

export function networkError(): AxiosError {
  return new AxiosError("Network Error", AxiosError.ERR_NETWORK);
}

export function httpError(status: number, message = `HTTP ${status}`): AxiosError {
  return new AxiosError(message, "ERR_BAD_RESPONSE", undefined, undefined, {
    status,
    data: { message },
  } as AxiosResponse);
}
