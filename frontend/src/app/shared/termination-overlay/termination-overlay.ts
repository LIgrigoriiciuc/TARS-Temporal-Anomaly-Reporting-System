import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth';
import { WebSocketService } from '../../core/services/websocket';
import { WS_TOPICS } from '../../core/services/ws-topics';

@Component({
  selector: 'app-termination-overlay',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './termination-overlay.html'
})
export class TerminationOverlay implements OnInit, OnDestroy {
  show = signal(false);
  private userId = localStorage.getItem('userId');

  constructor(
    private wsService  : WebSocketService,
    private authService: AuthService
  ) {}

  ngOnInit() {
    if (!this.userId) return;
    this.wsService.subscribe(WS_TOPICS.userDeactivated(this.userId), () => {
      this.show.set(true);
      setTimeout(() => {
        localStorage.clear();
        this.authService.logout().subscribe({
          next : () => { this.wsService.disconnect(); window.location.href = '/login'; },
          error: ()  => { this.wsService.disconnect(); window.location.href = '/login'; }
        });
      }, 3000);
    });
  }

  ngOnDestroy() {
    if (this.userId) this.wsService.unsubscribe(WS_TOPICS.userDeactivated(this.userId));
  }
}
