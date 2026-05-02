import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth';
import { DraftService } from '../../../core/services/draft';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit {
  drafts: any[] = [];
  timelines: any[] = [];
  email = localStorage.getItem('email') || '';
  selectedDraftId: number | null = null;
  draft = { description: '', year: null as number | null, keywords: '', timelineId: null as number | null };

  constructor(
    private authService: AuthService,
    private draftService: DraftService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.loadDrafts();
    this.loadTimelines();
  }

  loadDrafts() {
    this.draftService.getDrafts().subscribe({
      next: (data) => { this.drafts = data; this.cdr.detectChanges(); }
    });
  }

  loadTimelines() {
    this.draftService.getTimelines().subscribe({
      next: (data) => { this.timelines = data; }
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
      return;
    }

    if (this.selectedDraftId) {
      this.draftService.updateDraft(this.selectedDraftId, payload).subscribe({
        next: (updated) => {
          this.drafts = this.drafts.map(d => d.id === this.selectedDraftId ? updated : d);
          this.clearSelection();
          this.cdr.detectChanges();
        }
      });
    } else {
      this.draftService.saveDraft(payload).subscribe({
        next: (saved) => {
          this.drafts = [...this.drafts, saved];
          this.clearSelection();
          this.cdr.detectChanges();
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
    this.cdr.detectChanges();
  }

  clearSelection() {
    this.selectedDraftId = null;
    this.draft = { description: '', year: null, keywords: '', timelineId: null };
    this.cdr.detectChanges();
  }

  deleteDraft(id: number) {
    this.draftService.deleteDraft(id).subscribe({
      next: () => {
        this.drafts = this.drafts.filter(d => d.id !== id);
        if (this.selectedDraftId === id) this.clearSelection();
        this.cdr.detectChanges();
      }
    });
  }

  logout() {
    this.authService.logout().subscribe();
  }
}
