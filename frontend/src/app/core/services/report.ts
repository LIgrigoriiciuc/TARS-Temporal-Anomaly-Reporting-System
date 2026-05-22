import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private apiUrl = 'http://localhost:8080/api/reports';

  constructor(private http: HttpClient) {}

  // ── Drafts ──────────────────────────────────────────────────────────────────

  getDrafts() {
    return this.http.get<any[]>(`${this.apiUrl}/drafts`, { withCredentials: true });
  }

  saveDraft(dto: any) {
    return this.http.post<any>(`${this.apiUrl}/drafts`, dto, { withCredentials: true });
  }

  updateDraft(id: number, dto: any) {
    return this.http.put<any>(`${this.apiUrl}/drafts/${id}`, dto, { withCredentials: true });
  }

  deleteDraft(id: number) {
    return this.http.delete(`${this.apiUrl}/drafts/${id}`, { withCredentials: true });
  }

  /** UC-07 — promote an existing draft straight to a submission */
  submitFromDraft(id: number, dto: any) {
    return this.http.put<any>(`${this.apiUrl}/drafts/${id}/submit`, dto, { withCredentials: true });
  }

  // ── Submitted reports ────────────────────────────────────────────────────────

  /** UC-05 — submit a brand-new report (no prior draft) */
  submitReport(dto: any) {
    return this.http.post<any>(`${this.apiUrl}/submit`, dto, { withCredentials: true });
  }

  getSubmittedReports() {
    return this.http.get<any[]>(`${this.apiUrl}/submitted`, { withCredentials: true });
  }

  getSubmittedReport(id: number) {
    return this.http.get<any>(`${this.apiUrl}/submitted/${id}`, { withCredentials: true });
  }

  // ── Shared ───────────────────────────────────────────────────────────────────

  getTimelines() {
    return this.http.get<any[]>(`${this.apiUrl}/timelines`, { withCredentials: true });
  }
}
