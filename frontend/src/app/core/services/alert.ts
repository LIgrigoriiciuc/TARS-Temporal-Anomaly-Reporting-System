import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface AlertDTO {
  id          : number;
  anomalyId   : number;
  anomalyType : string;
  paradoxRisk : 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  timelineName: string;
  year        : number;
  acknowledged: boolean;
  createdAt   : string;
}

@Injectable({ providedIn: 'root' })
export class AlertService {
  private apiUrl = 'http://localhost:8080/api/admin/alerts';

  constructor(private http: HttpClient) {}

  getUnacknowledged() {
    return this.http.get<AlertDTO[]>(this.apiUrl, { withCredentials: true });
  }

  acknowledge(id: number) {
    return this.http.patch<AlertDTO>(`${this.apiUrl}/${id}/acknowledge`, {}, { withCredentials: true });
  }
}
