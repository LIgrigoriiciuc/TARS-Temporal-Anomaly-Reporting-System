import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface SubscriptionDTO {
  plan               : 'FREE' | 'PRO' | 'ENTERPRISE';
  billingCycle       : 'MONTHLY' | 'ANNUAL' | null;
  startDate          : string | null;
  expiryDate         : string | null;
  cancellationScheduled: boolean;
  timelinesAllowed   : number;
  reportsAllowed     : number;  // -1 = unlimited
  reportsUsed        : number;
}

export interface TimelineDTO {
  id         : number;
  name       : string;
  description: string;
  accessible : boolean;
}

@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private base = 'http://localhost:8080/api/reports/subscription';

  constructor(private http: HttpClient) {}

  getSubscription() {
    return this.http.get<SubscriptionDTO>(this.base, { withCredentials: true });
  }

  getTimelines() {
    return this.http.get<TimelineDTO[]>(`${this.base}/timelines`, { withCredentials: true });
  }

  addTimeline(timelineId: number) {
    return this.http.post<TimelineDTO>(`${this.base}/timelines/${timelineId}`, {}, { withCredentials: true });
  }

  upgrade(plan: string, billingCycle: string) {
    return this.http.post<{ checkoutUrl: string }>(`${this.base}/upgrade`, { plan, billingCycle }, { withCredentials: true });
  }

  cancel() {
    return this.http.post<{ message: string }>(`${this.base}/cancel`, {}, { withCredentials: true });
  }
}
