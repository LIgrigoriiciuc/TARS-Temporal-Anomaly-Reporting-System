import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AlertService, AlertDTO } from '../../core/services/alert';
import { WebSocketService } from '../../core/services/websocket';
import { WS_TOPICS } from '../../core/services/ws-topics';

@Component({
  selector: 'app-alert-toasts',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './alert-toasts.html'
})
export class AlertToasts implements OnInit, OnDestroy {
  alerts = signal<AlertDTO[]>([]);

  constructor(
    private alertService: AlertService,
    private wsService   : WebSocketService
  ) {}

  ngOnInit() {
    // Load all unacknowledged on mount (handles "no supervisor was online" case)
    this.alertService.getUnacknowledged().subscribe({
      next: (data) => this.alerts.set(data)
    });

    // New alert pushed by backend
    this.wsService.subscribe(WS_TOPICS.alertsNew, (body: string) => {
      try {
        const alert: AlertDTO = JSON.parse(body);
        // Guard against duplicates (e.g. reconnect replay)
        this.alerts.update(list =>
          list.some(a => a.id === alert.id) ? list : [...list, alert]
        );
      } catch (e) {
        console.warn('Failed to parse alert WS message', e);
      }
    });

    // Alert acknowledged by any supervisor — remove from everyone's list
    this.wsService.subscribe(WS_TOPICS.alertsAcknowledged, (body: string) => {
      const id = Number(body);
      if (!isNaN(id)) {
        this.alerts.update(list => list.filter(a => a.id !== id));
      }
    });
  }

  ngOnDestroy() {
    this.wsService.unsubscribe(WS_TOPICS.alertsNew);
    this.wsService.unsubscribe(WS_TOPICS.alertsAcknowledged);
  }

  acknowledge(alert: AlertDTO) {
    this.alertService.acknowledge(alert.id).subscribe({
      next: () => {
        // Remove locally immediately — WS push will do the same for others
        this.alerts.update(list => list.filter(a => a.id !== alert.id));
      }
    });
  }
}
