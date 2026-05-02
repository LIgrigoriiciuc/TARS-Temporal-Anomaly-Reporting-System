import { Component, OnInit } from '@angular/core';
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
  draft = { description: '', year: null, keywords: '', timelineId: null };

  constructor(private authService: AuthService, private draftService: DraftService) {}

  ngOnInit() {
    this.loadDrafts();
    this.loadTimelines();
  }

  loadDrafts() {
    this.draftService.getDrafts().subscribe({ next: (data) => this.drafts = data });
  }

  saveDraft() {
    this.draftService.saveDraft(this.draft).subscribe({
      next: () => {
        this.draft = { description: '', year: null, keywords: '', timelineId: null };
        this.loadDrafts();
      }
    });
  }

  selectDraft(d: any) {
    this.draft = { description: d.description, year: d.year, keywords: d.keywords, timelineId: d.timelineId };
  }

  deleteDraft(id: number) {
    this.draftService.deleteDraft(id).subscribe({ next: () => this.loadDrafts() });
  }

  logout() {
    this.authService.logout().subscribe();
  }

  loadTimelines() {
    this.draftService.getTimelines().subscribe({ next: (data) => this.timelines = data });
  }
}
