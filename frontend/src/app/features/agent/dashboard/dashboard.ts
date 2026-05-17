import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth';
import { DraftService } from '../../../core/services/draft';
import { WebSocketService } from '../../../core/services/websocket';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit, OnDestroy {
  drafts = signal<any[]>([]);
  timelines = signal<any[]>([]);
  draftError = signal('');
  accountTerminated = signal(false);
  email = localStorage.getItem('email') || '';
  selectedDraftId: number | null = null;
  draft = { description: '', year: null as number | null, keywords: '', timelineId: null as number | null };
  private pollingInterval: any = null;

  constructor(
    private authService: AuthService,
    private draftService: DraftService,
    private wsService: WebSocketService
  ) {}

  ngOnInit() {
    this.loadDrafts();
    this.loadTimelines();
    this.pollingInterval = setInterval(() => this.loadDrafts(), 5000);

    const userId = localStorage.getItem('userId');
    if (userId) {
      this.wsService.connect(() => {
        this.accountTerminated.set(true);
        setTimeout(() => {
          localStorage.clear();
          this.wsService.disconnect();
          window.location.href = '/login';
        }, 3000);
      }, `/topic/user-deactivated/${userId}`);
    }
  }

  ngOnDestroy() {
    clearInterval(this.pollingInterval);
  }

  loadDrafts() {
    this.draftService.getDrafts().subscribe({
      next: (data) => {
        const incoming = JSON.stringify(data);
        const current = JSON.stringify(this.drafts());
        if (incoming !== current) {
          this.drafts.set(data);
        }
      },
      error: (err) => {
        if (err.status === 401 || err.status === 403) {
          this.accountTerminated.set(true);
          setTimeout(() => {
            localStorage.clear();
            this.wsService.disconnect();
            this.authService.logout().subscribe({
              next: () => window.location.href = '/login',
              error: () => window.location.href = '/login'
            });
          }, 3000);
        }
      }
    });
  }

  loadTimelines() {
    this.draftService.getTimelines().subscribe({
      next: (data) => this.timelines.set(data)
    });
  }

  saveDraft() {
    const payload = {
      description: this.draft.description,
      year: this.draft.year,
      keywords: this.draft.keywords,
      timelineId: this.draft.timelineId
    };

    if (!payload.description && !payload.year && !payload.keywords) {
      this.draftError.set('At least one field must be filled');
      return;
    }
    this.draftError.set('');

    if (this.selectedDraftId) {
      this.draftService.updateDraft(this.selectedDraftId, payload).subscribe({
        next: () => {
          this.clearSelection();
          this.loadDrafts();
        }
      });
    } else {
      this.draftService.saveDraft(payload).subscribe({
        next: () => {
          this.clearSelection();
          this.loadDrafts();
        }
      });
    }
  }

  selectDraft(d: any) {
    this.selectedDraftId = d.id;
    this.draft = {
      description: d.description || '',
      year: d.year,
      keywords: d.keywords || '',
      timelineId: d.timelineId
    };
  }

  clearSelection() {
    this.selectedDraftId = null;
    this.draft = { description: '', year: null, keywords: '', timelineId: null };
  }

  deleteDraft(id: number) {
    this.draftService.deleteDraft(id).subscribe({
      next: () => {
        if (this.selectedDraftId === id) this.clearSelection();
        this.loadDrafts();
      }
    });
  }

  logout() {
    clearInterval(this.pollingInterval);
    this.wsService.disconnect();
    this.authService.logout().subscribe();
  }
}
