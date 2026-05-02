import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class DraftService {
  private apiUrl = 'http://localhost:8080/api/reports';

  constructor(private http: HttpClient) {}

  getDrafts() {
    return this.http.get<any[]>(`${this.apiUrl}/drafts`, { withCredentials: true });
  }

  saveDraft(dto: any) {
    return this.http.post(`${this.apiUrl}/drafts`, dto, { withCredentials: true });
  }

  deleteDraft(id: number) {
    return this.http.delete(`${this.apiUrl}/drafts/${id}`, { withCredentials: true });
  }
}
