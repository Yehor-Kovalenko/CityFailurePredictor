import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { AuthSession, UserRole } from "../types";
import authService from "../services/auth";
import {
  getAuthChangedEventName,
  getStoredSession,
} from "../services/authStorage";

type AuthContextValue = {
  session: AuthSession | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  loginWithGoogle: () => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (role: UserRole) => boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const currentSession = authService.getCurrentSession();
    setSession(currentSession);
    setIsLoading(false);

    const syncSession = () => {
      setSession(getStoredSession());
    };

    const authChangedEvent = getAuthChangedEventName();

    window.addEventListener("storage", syncSession);
    window.addEventListener(authChangedEvent, syncSession);

    return () => {
      window.removeEventListener("storage", syncSession);
      window.removeEventListener(authChangedEvent, syncSession);
    };
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      isAuthenticated: Boolean(session),
      isLoading,
      loginWithGoogle: async () => {
        const nextSession = await authService.loginWithGoogle();
        setSession(nextSession);
      },
      logout: async () => {
        await authService.logout();
        setSession(null);
      },
      hasRole: (role: UserRole) => session?.user.role === role,
    }),
    [isLoading, session],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
