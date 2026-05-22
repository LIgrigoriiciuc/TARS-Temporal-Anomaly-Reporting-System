import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth';
import { ReportService } from '../../../core/services/report';
import { WebSocketService } from '../../../core/services/websocket';
import { WS_TOPICS } from '../../../core/services/ws-topics';
import { Sidebar } from '../../../shared/sidebar/sidebar';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, Sidebar],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit, OnDestroy {
  // ── Signals ─────────────────────────────────────────────────────────────────
  drafts            = signal<any[]>([]);
  timelines         = signal<any[]>([]);
  submittedReports  = signal<any[]>([]);
  draftError        = signal('');
  submitError       = signal('');
  accountTerminated = signal(false);

  // ── State ────────────────────────────────────────────────────────────────────
  email            = localStorage.getItem('email') || '';
  selectedDraftId : number | null = null;
  selectedReportId: number | null = null;
  activeTab: 'drafts' | 'submitted' = 'drafts';

  draft = {
    description: '',
    year       : null as number | null,
    keywords   : '',
    timelineId : null as number | null
  };

  private pollingInterval: any = null;

  constructor(
    private authService  : AuthService,
    private reportService: ReportService,
    private wsService    : WebSocketService
  ) {}

  // ── Lifecycle ────────────────────────────────────────────────────────────────

  ngOnInit() {
    this.loadDrafts();
    this.loadTimelines();
    this.loadSubmittedReports();

    this.pollingInterval = setInterval(() => {
      this.loadDrafts();
      this.loadSubmittedReports();
    }, 5000);

    const userId = localStorage.getItem('userId');
    if (userId) {
      this.wsService.connect(() => {
        this.wsService.subscribe(WS_TOPICS.userDeactivated(userId), () => {
          this.handleAccountTermination();
        });
        this.wsService.subscribe(WS_TOPICS.analysisResult(userId), (body: string) => {
          this.handleAnalysisResult(body);
        });
      });
    }
  }

  ngOnDestroy() {
    clearInterval(this.pollingInterval);
    this.wsService.disconnect();
  }

  // ── Data loading ─────────────────────────────────────────────────────────────

  loadDrafts() {
    this.reportService.getDrafts().subscribe({
      next : (data) => {
        if (JSON.stringify(data) !== JSON.stringify(this.drafts())) this.drafts.set(data);
      },
      error: (err) => this.handleAuthError(err)
    });
  }

  loadTimelines() {
    this.reportService.getTimelines().subscribe({
      next: (data) => this.timelines.set(data)
    });
  }

  loadSubmittedReports() {
    this.reportService.getSubmittedReports().subscribe({
      next : (data) => {
        if (JSON.stringify(data) !== JSON.stringify(this.submittedReports())) {
          this.submittedReports.set(data);
        }
      },
      error: (err) => this.handleAuthError(err)
    });
  }

  // ── Draft actions ────────────────────────────────────────────────────────────

  saveDraft() {
    const payload = this.buildPayload();
    if (!this.validatePayload(payload, 'draftError')) return;

    const call = this.selectedDraftId
      ? this.reportService.updateDraft(this.selectedDraftId, payload)
      : this.reportService.saveDraft(payload);

    call.subscribe({
      next: () => { this.clearSelection(); this.loadDrafts(); }
    });
  }

  deleteDraft(id: number) {
    this.reportService.deleteDraft(id).subscribe({
      next: () => {
        if (this.selectedDraftId === id) this.clearSelection();
        this.loadDrafts();
      }
    });
  }

  selectDraft(d: any) {
    this.selectedDraftId = d.id;
    this.draft = {
      description: d.description || '',
      year       : d.year,
      keywords   : d.keywords || '',
      timelineId : d.timelineId
    };
  }

  clearSelection() {
    this.selectedDraftId = null;
    this.draft = { description: '', year: null, keywords: '', timelineId: null };
  }

  // ── Submission (UC-05 / UC-07) ────────────────────────────────────────────────

  commitReport() {
    const payload = this.buildPayload();
    if (!this.validatePayload(payload, 'submitError')) return;

    const call = this.selectedDraftId
      ? this.reportService.submitFromDraft(this.selectedDraftId, payload)
      : this.reportService.submitReport(payload);

    call.subscribe({
      next: () => {
        this.clearSelection();
        this.loadDrafts();
        this.loadSubmittedReports();
        this.activeTab = 'submitted';
      },
      error: (err: any) => {
        this.submitError.set(err?.error?.message || 'Submission failed. Try again.');
      }
    });
  }

  // ── WebSocket handlers ────────────────────────────────────────────────────────

  private handleAnalysisResult(body: string) {
    try {
      const updated = JSON.parse(body);
      // Replace the matching report in the list with the server's full updated DTO
      this.submittedReports.update(reports =>
        reports.map(r => r.id === updated.id ? updated : r)
      );
    } catch (e) {
      console.warn('Failed to parse analysis WS message', e);
    }
  }

  // ── Submitted report selection ────────────────────────────────────────────────

  selectReport(r: any) {
    this.selectedReportId = this.selectedReportId === r.id ? null : r.id;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  private buildPayload() {
    return {
      description: this.draft.description,
      year       : this.draft.year,
      keywords   : this.draft.keywords,
      timelineId : this.draft.timelineId
    };
  }

  private validatePayload(payload: any, errorSignal: 'draftError' | 'submitError'): boolean {
    if (!payload.description && !payload.year && !payload.keywords) {
      this[errorSignal].set('At least one field must be filled.');
      return false;
    }
    this[errorSignal].set('');
    return true;
  }

  private handleAuthError(err: any) {
    if (err.status === 401 || err.status === 403) this.handleAccountTermination();
  }

  private handleAccountTermination() {
    this.accountTerminated.set(true);
    setTimeout(() => {
      clearInterval(this.pollingInterval);
      localStorage.clear();
      this.authService.logout().subscribe({
        next : () => { this.wsService.disconnect(); window.location.href = '/login'; },
        error: ()  => { this.wsService.disconnect(); window.location.href = '/login'; }
      });
    }, 3000);
  }

  logout() {
    clearInterval(this.pollingInterval);
    this.wsService.disconnect();
    this.authService.logout().subscribe({
      next : () => window.location.href = '/login',
      error: () => window.location.href = '/login'
    });
  }
}
