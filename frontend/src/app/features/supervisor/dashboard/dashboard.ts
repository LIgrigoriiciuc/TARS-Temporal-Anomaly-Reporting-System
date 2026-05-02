import { Component, OnInit } from '@angular/core';
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
  showCreateForm = false;
  currentTime = '';
  newUser = { name: '', email: '', password: '', role: 'AGENT' };

  constructor(private authService: AuthService, private userService: UserService, private router: Router) {}

  ngOnInit() {
    this.loadUsers();
    this.updateTime();
    setInterval(() => this.updateTime(), 1000);
  }

  updateTime() {
    this.currentTime = new Date().toISOString().replace('T', '_').substring(0, 19);
  }

  loadUsers() {
    this.userService.getAllUsers().subscribe({ next: (data) => this.users = data });
  }

  createUser() {
    this.userService.createUser(this.newUser).subscribe({
      next: (created) => {
        this.users = [...this.users, created];
        this.showCreateForm = false;
        this.newUser = { name: '', email: '', password: '', role: 'AGENT' };
      }
    });
  }

  deactivateUser(id: number) {
    this.userService.deactivateUser(id).subscribe({
      next: () => {
        this.users = this.users.map(u => u.id === id ? {...u, status: 'INACTIVE'} : u);
      }
    });
  }

  logout() {
    this.authService.logout().subscribe();
  }
}
