import axios, { AxiosInstance } from "axios";
import { AuthSession } from "../types";
import {
  clearStoredSession,
  getStoredSession,
  saveStoredSession,
  toSession,
} from "./authStorage";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:9191";
const LOGIN_TIMEOUT_MS = 2 * 60 * 1000;
const POLL_INTERVAL_MS = 1200;

class AuthService {
  private api: AxiosInstance;

  constructor() {
    this.api = axios.create({
      baseURL: API_BASE_URL,
      withCredentials: true,
    });
  }

  getCurrentSession(): AuthSession | null {
    return getStoredSession();
  }

  async loginWithGoogle(): Promise<AuthSession> {
    const popup = window.open(
      `${API_BASE_URL}/oauth2/authorization/google`,
      "city-failure-predictor-google-login",
      "popup=yes,width=520,height=700",
    );

    if (!popup) {
      throw new Error(
        "Popup blocked by browser. Please allow popups and try again.",
      );
    }

    const startedAt = Date.now();
    let requestInFlight = false;

    return new Promise<AuthSession>((resolve, reject) => {
      const timerId = window.setInterval(async () => {
        if (Date.now() - startedAt > LOGIN_TIMEOUT_MS) {
          window.clearInterval(timerId);
          if (!popup.closed) {
            popup.close();
          }
          reject(new Error("Login timed out. Please try again."));
          return;
        }

        if (requestInFlight) {
          return;
        }

        requestInFlight = true;

        try {
          const response = await this.api.get("/auth/success", {
            validateStatus: (status) => status >= 200 && status < 500,
          });

          if (response.status === 200 && response.data?.accessToken) {
            const session = toSession(response.data);
            saveStoredSession(session);
            window.clearInterval(timerId);
            if (!popup.closed) {
              popup.close();
            }
            resolve(session);
            return;
          }

          if (popup.closed && response.status !== 200) {
            window.clearInterval(timerId);
            reject(new Error("Login was canceled before completion."));
          }
        } catch {
          if (popup.closed) {
            window.clearInterval(timerId);
            reject(new Error("Login was canceled before completion."));
          }
        } finally {
          requestInFlight = false;
        }
      }, POLL_INTERVAL_MS);
    });
  }

  async logout(): Promise<void> {
    clearStoredSession();

    try {
      await this.api.post(
        "/logout",
        {},
        {
          validateStatus: (status) => status >= 200 && status < 500,
        },
      );
    } catch {}
  }
}

export default new AuthService();
