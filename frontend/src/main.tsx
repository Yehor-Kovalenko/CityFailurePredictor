import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import { AuthProvider } from "./auth/AuthContext";
import { UpdatePrompt } from "./components/UpdatePrompt";
import { startSyncManager } from "./offline/syncManager";
import "./index.css";

startSyncManager();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AuthProvider>
      <App />
      <UpdatePrompt />
    </AuthProvider>
  </React.StrictMode>,
);
