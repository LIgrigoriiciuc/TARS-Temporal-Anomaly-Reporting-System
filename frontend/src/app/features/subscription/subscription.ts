import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SubscriptionService, SubscriptionDTO, TimelineDTO } from '../../core/services/subscription';
import { WebSocketService } from '../../core/services/websocket';
import { WS_TOPICS } from '../../core/services/ws-topics';
import { Sidebar } from '../../shared/sidebar/sidebar';
import { TerminationOverlay } from '../../shared/termination-overlay/termination-overlay';

@Component({
  selector: 'app-subscription',
  standalone: true,
  imports: [CommonModule, FormsModule, Sidebar, TerminationOverlay],
  templateUrl: './subscription.html'
})
export class SubscriptionPage implements OnInit, OnDestroy {
  subscription      = signal<SubscriptionDTO | null>(null);
  timelines         = signal<TimelineDTO[]>([]);
  loading           = signal(true);
  error             = signal('');
  upgradeError      = signal('');
  cancelError       = signal('');
  justUpgraded      = signal(false);
  billingCycle: 'MONTHLY' | 'ANNUAL' = 'MONTHLY';
  showCancelConfirm = signal(false);
  cancelMessage     = signal('');
  showTimelinePicker = signal(false);
  pickerLoading     = signal(false);
  pickerError       = signal('');

  slotsRemaining = computed(() => {
    const sub = this.subscription();
    if (!sub) return 0;
    const accessible = this.timelines().filter(t => t.accessible).length;
    if (sub.plan === 'ENTERPRISE') return 0;
    return Math.max(0, sub.timelinesAllowed - accessible);
  });

  readonly plans = [
    {
      key     : 'PRO',
      label   : 'PROFESSIONAL',
      monthly : 49,
      annual  : 39,
      timelines: 5,
      reports : 200,
      features: ['5 timeline slots', '200 reports/month', 'AI analysis priority'],
    },
    {
      key     : 'ENTERPRISE',
      label   : 'ENTERPRISE',
      monthly : 299,
      annual  : 249,
      timelines: -1,
      reports : -1,
      features: ['Unlimited timelines', 'Unlimited reports', 'Full temporal override'],
    }
  ];

  private agentId = localStorage.getItem('userId') || '';

  constructor(
    private subService: SubscriptionService,
    private wsService : WebSocketService,
    private route     : ActivatedRoute
  ) {}

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['upgraded'] === 'true') this.justUpgraded.set(true);
    });

    this.load();
    this.wsService.connect();

    this.wsService.subscribe(WS_TOPICS.subscriptionUpdated(this.agentId), (body: string) => {
      try {
        const updated: SubscriptionDTO = JSON.parse(body);
        this.subscription.set(updated);
        this.justUpgraded.set(true);
        this.loadTimelines();
        if (updated.plan === 'PRO' && updated.timelinesAllowed > 0) {
          this.showTimelinePicker.set(true);
        }
      } catch (e) {
        console.warn('Failed to parse subscription WS message', e);
      }
    });
  }

  ngOnDestroy() {
    this.wsService.unsubscribe(WS_TOPICS.subscriptionUpdated(this.agentId));
  }

  load() {
    this.loading.set(true);
    this.subService.getSubscription().subscribe({
      next: (sub) => {
        this.subscription.set(sub);
        this.loadTimelines();
        this.loading.set(false);
        if (sub.plan === 'PRO' && this.slotsRemaining() > 0) {
          this.showTimelinePicker.set(true);
        }
      },
      error: () => { this.error.set('Failed to load subscription.'); this.loading.set(false); }
    });
  }

  loadTimelines() {
    this.subService.getTimelines().subscribe({
      next: (data) => this.timelines.set(data)
    });
  }

  upgrade(planKey: string) {
    this.upgradeError.set('');
    this.subService.upgrade(planKey, this.billingCycle).subscribe({
      next : (res) => window.location.href = res.checkoutUrl,
      error: (err) => this.upgradeError.set(err?.error?.message || 'Payment service unavailable. Try again.')
    });
  }

  confirmCancel() {
    this.cancelError.set('');
    this.subService.cancel().subscribe({
      next: (res) => { this.cancelMessage.set(res.message); this.showCancelConfirm.set(false); this.load(); },
      error: (err) => { this.cancelError.set(err?.error?.message || 'Cancellation failed. Try again.'); this.showCancelConfirm.set(false); }
    });
  }

  addTimeline(timeline: TimelineDTO) {
    if (timeline.accessible || this.slotsRemaining() <= 0) return;
    this.pickerError.set('');
    this.pickerLoading.set(true);
    this.subService.addTimeline(timeline.id).subscribe({
      next: () => {
        this.loadTimelines();
        this.pickerLoading.set(false);
        if (this.slotsRemaining() <= 1) setTimeout(() => this.showTimelinePicker.set(false), 400);
      },
      error: (err) => { this.pickerError.set(err?.error?.message || 'Failed to add timeline.'); this.pickerLoading.set(false); }
    });
  }

  price(plan: any): number { return this.billingCycle === 'MONTHLY' ? plan.monthly : plan.annual; }
  isCurrentPlan(planKey: string): boolean { return this.subscription()?.plan === planKey; }
  isDowngrade(planKey: string): boolean {
    const order = { FREE: 0, PRO: 1, ENTERPRISE: 2 };
    const current = this.subscription()?.plan ?? 'FREE';
    return (order[planKey as keyof typeof order] ?? 0) < (order[current as keyof typeof order] ?? 0);
  }
}
