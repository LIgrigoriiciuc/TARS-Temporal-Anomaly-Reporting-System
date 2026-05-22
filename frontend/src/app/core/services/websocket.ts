import { Injectable } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private client!: Client;
  private subscriptions = new Map<string, StompSubscription>();

  // Pending subscriptions requested before connect() was called
  private pendingSubscriptions: Array<{ topic: string; handler: (body: string) => void }> = [];

  connect(onConnect?: () => void) {
    if (this.client?.active) {
      onConnect?.();
      return;
    }

    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      onConnect: () => {
        // Flush anything that was subscribed before the connection was ready
        for (const { topic, handler } of this.pendingSubscriptions) {
          this.doSubscribe(topic, handler);
        }
        this.pendingSubscriptions = [];
        onConnect?.();
      },
      reconnectDelay: 5000,
    });

    this.client.activate();
  }

  /**
   * Subscribe to a STOMP topic.
   * Safe to call before connect() — the subscription is queued and flushed
   * once the connection is established.
   * Returns an unsubscribe function for easy cleanup.
   */
  subscribe(topic: string, handler: (body: string) => void): () => void {
    if (this.client?.connected) {
      this.doSubscribe(topic, handler);
    } else {
      // Queue for when the connection opens (or re-opens after reconnect)
      this.pendingSubscriptions.push({ topic, handler });
    }
    return () => this.unsubscribe(topic);
  }

  unsubscribe(topic: string) {
    this.subscriptions.get(topic)?.unsubscribe();
    this.subscriptions.delete(topic);
  }

  disconnect() {
    this.subscriptions.clear();
    this.pendingSubscriptions = [];
    this.client?.deactivate();
  }

  private doSubscribe(topic: string, handler: (body: string) => void) {
    if (this.subscriptions.has(topic)) return; // already subscribed
    const sub = this.client.subscribe(topic, (msg) => handler(msg.body));
    this.subscriptions.set(topic, sub);
  }
}
