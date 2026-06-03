import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { UserService } from '../../../core/services/user';
import { WebSocketService } from '../../../core/services/websocket';
import { WS_TOPICS } from '../../../core/services/ws-topics';
import { Sidebar } from '../../../shared/sidebar/sidebar';
import { AlertToasts } from '../../../shared/alert-toasts/alert-toasts';
import {TerminationOverlay} from '../../../shared/termination-overlay/termination-overlay';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, Sidebar, AlertToasts, TerminationOverlay],  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit, OnDestroy {
  users           = signal<any[]>([]);
  currentTime     = signal('');
  createError     = signal('');
  deactivateError = signal('');
  newUser = { name: '', email: '', password: '', role: 'AGENT' };
  private pollingInterval: any = null;

  constructor(
    private authService: AuthService,
    private userService: UserService,
    private wsService  : WebSocketService,
    private router     : Router,
  ) {}

  ngOnInit() {
    this.loadUsers();
    this.updateTime();
    setInterval(() => this.updateTime(), 1000);
    this.pollingInterval = setInterval(() => this.loadUsers(), 5000);

    // WS connection needed for alert toasts (AlertToasts subscribes on its own,
    // but the connection itself must be opened here)
    this.wsService.connect();
  }

  ngOnDestroy() {
    clearInterval(this.pollingInterval);
  }

  updateTime() {
    this.currentTime.set(new Date().toISOString().replace('T', '_').substring(0, 19));
  }

  loadUsers() {
    this.userService.getAllUsers().subscribe({
      next: (data) => {
        const sorted   = data.sort((a: any, b: any) => a.id - b.id);
        const current  = JSON.stringify(this.users());
        const incoming = JSON.stringify(sorted);
        if (current !== incoming) this.users.set(sorted);
      },
      error: (err) => console.error('loadUsers error:', err)
    });
  }

  createUser() {
    this.createError.set('');
    this.deactivateError.set('');
    const payload = { ...this.newUser };
    this.newUser = { name: '', email: '', password: '', role: 'AGENT' };

    this.userService.createUser(payload).subscribe({
      next : () => this.loadUsers(),
      error: (err) => {
        console.log('STATUS:', err.status);
        console.log('ERROR BODY:', err.error);
        this.newUser = payload;
        if (err.status === 409) {
          this.createError.set('Email already registered');
        } else if (err.status === 400) {
          const errors = err.error?.errors;
          if      (errors?.email)    this.createError.set('Invalid email format');
          else if (errors?.password) this.createError.set('Password must be at least 6 characters');
          else if (errors?.name)     this.createError.set('Name is required');
          else                       this.createError.set('Invalid input');
        } else {
          this.createError.set('Failed to create user');
        }
      }
    });
  }

  deactivateUser(id: number) {
    this.deactivateError.set('');
    this.createError.set('');
    this.userService.deactivateUser(id).subscribe({
      next : () => this.loadUsers(),
      error: (err) => {
        this.deactivateError.set(
          err.status === 403 ? 'Cannot terminate your own access' : 'Failed to terminate access'
        );
      }
    });
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
