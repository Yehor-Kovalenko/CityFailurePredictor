import axios, {AxiosInstance} from "axios";
import {AuthResponse, AuthSession} from "../types";
import {clearStoredSession, getStoredSession, saveStoredSession, toSession,} from "./authStorage";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:9191";
const LOGIN_TIMEOUT_MS = 2 * 60 * 1000;

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
        const width = 900;
        const height = 700;
        const left = window.screenX + (window.outerWidth - width) / 2;
        const top = window.screenY + (window.outerHeight - height) / 2;

        const popup = window.open(
            `${API_BASE_URL}/oauth2/authorization/google`,
            "city-failure-predictor-google-login",
            `popup=yes,width=${width},height=${height},left=${left},top=${top}`,
        );

        if (!popup) {
            throw new Error("Popup blocked by browser. Please allow popups and try again.");
        }

        return new Promise<AuthSession>((resolve, reject) => {
            const timeoutId = window.setTimeout(() => {
                window.removeEventListener("message", handleMessage);
                if (!popup.closed) {
                    popup.close();
                }
                reject(new Error("Login timed out. Please try again."));
            }, LOGIN_TIMEOUT_MS);

            const handleMessage = (event: MessageEvent<AuthResponse>) => {
                if (event.origin !== API_BASE_URL) {
                    return;
                }

                if (!event.data?.accessToken) {
                    return;
                }

                const session = toSession(event.data);
                saveStoredSession(session);

                window.clearTimeout(timeoutId);
                window.removeEventListener("message", handleMessage);

                if (!popup.closed) {
                    popup.close();
                }

                resolve(session);
            };

            window.addEventListener("message", handleMessage);
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
        } catch {
        }
    }
}

export default new AuthService();
