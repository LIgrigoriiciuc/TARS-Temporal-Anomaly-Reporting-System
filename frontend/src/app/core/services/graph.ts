import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

export interface AnomalyGraphDTO {
  id: number;
  year: number;
  type: string;
  paradoxRisk: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  timelineId: number;
  timelineName: string;
}

export interface GraphFilters {
  timelineId?: number | null;
  paradoxRisk?: string | null;
  yearFrom?: number | null;
  yearTo?: number | null;
}

@Injectable({ providedIn: 'root' })
export class GraphService {
  private apiUrl = 'http://localhost:8080/api/graph';

  constructor(private http: HttpClient) {}

  getAnomalies(filters: GraphFilters = {}) {
    let params = new HttpParams();
    if (filters.timelineId != null) params = params.set('timelineId', filters.timelineId);
    if (filters.paradoxRisk)        params = params.set('paradoxRisk', filters.paradoxRisk);
    if (filters.yearFrom != null)   params = params.set('yearFrom', filters.yearFrom);
    if (filters.yearTo != null)     params = params.set('yearTo', filters.yearTo);
    return this.http.get<AnomalyGraphDTO[]>(`${this.apiUrl}/anomalies`, { params, withCredentials: true });
  }

  getTimelines() {
    return this.http.get<any[]>(`${this.apiUrl}/timelines`, { withCredentials: true });
  }
}
