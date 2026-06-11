import axios, { AxiosInstance } from "axios";
import {
  Incident,
  CreateIncidentRequest,
  UpdateIncidentStatusRequest,
  IncidentStatus,
  IncidentType,
} from "../types";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

class IncidentService {
  private api: AxiosInstance;

  constructor() {
    this.api = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        "Content-Type": "application/json",
      },
    });
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

export default new IncidentService();
