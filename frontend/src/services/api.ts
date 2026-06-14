import axios, { AxiosInstance } from "axios";
import {
  Incident,
  CreateIncidentRequest,
  UpdateIncidentStatusRequest,
  IncidentStatus,
  IncidentType, Notification,
} from "../types";
import { clearStoredSession, getStoredSession } from "./authStorage";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:9191";

class IncidentService {
  private api: AxiosInstance;

  constructor() {
    this.api = axios.create({
      baseURL: API_BASE_URL,
      withCredentials: true,
      headers: {
        "Content-Type": "application/json",
      },
    });

    this.api.interceptors.request.use((config) => {
      const session = getStoredSession();
      if (session?.accessToken) {
        config.headers.Authorization = `Bearer ${session.accessToken}`;
      }
      return config;
    });

    this.api.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error?.response?.status === 401) {
          clearStoredSession();
        }
        return Promise.reject(error);
      },
    );
  }

  async createIncident(request: CreateIncidentRequest): Promise<Incident> {
    const response = await this.api.post<Incident>("/incidents", request);
    return response.data;
  }

  async getIncidentById(id: string): Promise<Incident> {
    const response = await this.api.get<Incident>(`/incidents/${id}`);
    return response.data;
  }

  async getAllIncidents(
    status?: IncidentStatus,
    type?: IncidentType,
  ): Promise<Incident[]> {
    const params: Record<string, string> = {};
    if (status) params.status = status;
    if (type) params.type = type;

    const response = await this.api.get<Incident[]>("/incidents/all", {
      params,
    });
    return response.data;
  }

  async updateIncidentStatus(
    id: string,
    request: UpdateIncidentStatusRequest,
  ): Promise<Incident> {
    const response = await this.api.patch<Incident>(
      `/incidents/${id}/status`,
      request,
    );
    return response.data;
  }

  async deleteIncident(id: string): Promise<void> {
    await this.api.delete(`/incidents/${id}`);
  }

  async getStatuses(): Promise<IncidentStatus[]> {
    const response = await this.api.get<IncidentStatus[]>(
      "/incidents/statuses",
    );
    return response.data;
  }
}

class NotificationService {
  private api: AxiosInstance;

  constructor() {
    this.api = axios.create({
      baseURL: API_BASE_URL,
      withCredentials: true,
      headers: {
        "Content-Type": "application/json",
      },
    });

    this.api.interceptors.request.use((config) => {
      const session = getStoredSession();
      if (session?.accessToken) {
        config.headers.Authorization = `Bearer ${session.accessToken}`;
      }
      return config;
    });

    this.api.interceptors.response.use(
        (response) => response,
        (error) => {
          if (error?.response?.status === 401) {
            clearStoredSession();
          }
          return Promise.reject(error);
        },
    );
  }

  async getNotifications(): Promise<Notification[]> {
    const response = await this.api.get<Notification[]>("/notifications");
    return response.data;
  }

  async markNotificationAsRead(id: string): Promise<Notification> {
    const response = await this.api.patch<Notification>(`/notifications/${id}/read`);
    return response.data;
  }
}

export const incidentService = new IncidentService();
export const notificationService = new NotificationService();
