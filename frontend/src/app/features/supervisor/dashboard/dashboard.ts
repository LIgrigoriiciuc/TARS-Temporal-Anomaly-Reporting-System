import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { UserService } from '../../../core/services/user';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit {
  users: any[] = [];
  currentTime = '';
  newUser = { name: '', email: '', password: '', role: 'AGENT' };
  private pollingInterval: any = null
  constructor(
    private authService: AuthService,
    private userService: UserService,
    private router: Router,
    private cdr: ChangeDetectorRef

  ) {}

  ngOnInit() {
    this.loadUsers();
    this.updateTime();
    setInterval(() => this.updateTime(), 1000);
    this.pollingInterval = setInterval(() => this.loadUsers(), 5000);
  }

  updateTime() {
    this.currentTime = new Date().toISOString().replace('T', '_').substring(0, 19);
  }

  loadUsers() {
    this.userService.getAllUsers().subscribe({
      next: (data) => {
        this.users = data.sort((a, b) => a.id - b.id);
        this.cdr.detectChanges();
      },
      error: (err) => console.error('loadUsers error:', err)
    });
  }

  createUser() {
    const payload = {
      name: this.newUser.name,
      email: this.newUser.email,
      password: this.newUser.password,
      role: this.newUser.role
    };

    this.userService.createUser(payload).subscribe({
      next: () => {
        this.newUser = { name: '', email: '', password: '', role: 'AGENT' };
        this.loadUsers();
      },
      error: (err) => {
        alert('ERROR: ' + (err.error?.message || 'Could not create user'));
      }
    });
  }

  deactivateUser(id: number) {
    this.userService.deactivateUser(id).subscribe({
      next: () => this.loadUsers()
    });
  }

  logout() {
    clearInterval(this.pollingInterval);
    this.authService.logout().subscribe();
  }
}
