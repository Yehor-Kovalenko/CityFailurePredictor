import { AlertTriangle, ShieldCheck } from "lucide-react";

interface LoginScreenProps {
  onLogin: () => Promise<void>;
  loading: boolean;
  error: string | null;
}

export function LoginScreen({ onLogin, loading, error }: LoginScreenProps) {
  return (
    <main className="min-h-dvh bg-dark-950 text-white flex items-center justify-center p-4 sm:p-6">
      <div className="w-full max-w-xl rounded-2xl border border-dark-700 bg-gradient-to-br from-dark-800 to-dark-900 shadow-2xl shadow-black/40 p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="shrink-0 h-12 w-12 rounded-lg bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center">
            <ShieldCheck size={24} />
          </div>
          <div>
            <h1 className="text-xl sm:text-2xl font-bold">City Failure Predictor</h1>
            <p className="text-dark-300">
              Sign in to access incidents and operations dashboard.
            </p>
          </div>
        </div>

        {error && (
          <div className="mb-4 rounded-lg border border-red-800 bg-red-900/30 px-4 py-3 text-red-300 text-sm flex items-start gap-2">
            <AlertTriangle size={16} className="mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        <button
          onClick={() => {
            void onLogin();
          }}
          disabled={loading}
          className="w-full rounded-lg bg-gradient-to-r from-blue-600 to-blue-700 px-4 py-3 font-semibold hover:shadow-lg hover:shadow-blue-700/40 disabled:opacity-60 disabled:cursor-not-allowed transition-all"
        >
          {loading ? "Signing in..." : "Continue with Google"}
        </button>
      </div>
    </main>
  );
}
