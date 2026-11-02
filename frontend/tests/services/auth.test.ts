import {afterEach, describe, expect, it, vi} from "vitest";
import authService from "@/services/auth";
import {getStoredSession, saveStoredSession} from "@/services/authStorage";
import {readGeoConsent, storeGeoConsent} from "@/services/geoConsent";
import {AuthSession} from "@/types";

const api = (authService as unknown as { api: { post: (...args: unknown[]) => Promise<unknown> } }).api;

describe("authService.logout", () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it("forgets the session and the location consent of the user", async () => {
        vi.spyOn(api, "post").mockResolvedValue({status: 200});
        saveStoredSession({
            accessToken: "token",
            tokenType: "Bearer",
            expiresAt: Date.now() + 60_000,
            user: {email: "user@example.com"},
        } as AuthSession);
        storeGeoConsent("granted");
        expect(getStoredSession()).not.toBeNull();

        await authService.logout();

        expect(getStoredSession()).toBeNull();
        expect(readGeoConsent()).toBe("unknown");
    });

    it("still logs out locally when the server cannot be reached", async () => {
        vi.spyOn(api, "post").mockRejectedValue(new Error("offline"));
        storeGeoConsent("denied");

        await expect(authService.logout()).resolves.toBeUndefined();
        expect(readGeoConsent()).toBe("unknown");
    });
});
