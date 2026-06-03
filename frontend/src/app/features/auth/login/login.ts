import {Component, signal} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './login.html',
})
export class Login {
  email = '';
  password = '';
  errorMessage = signal('');
  private isLoading = false;

  constructor(private authService: AuthService, private router: Router) {
    console.log('Login component created');
  }

  onLogin() {
    if (this.isLoading) return;
    this.isLoading = true;

    localStorage.removeItem('role');
    localStorage.removeItem('email');
    localStorage.removeItem('userId');

    this.authService.login(this.email, this.password).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.role === 'Supervisor') {
          this.router.navigate(['/supervisor']);
        } else {
          this.router.navigate(['/agent']);
        }
      },
      error: (err) => {
        this.isLoading = false;
        if (err.status === 403) {
          this.errorMessage.set('ACCOUNT_TERMINATED // Access denied');
        } else if (err.status === 401) {
          this.errorMessage.set('Invalid credentials');
        } else {
          this.errorMessage.set('SYSTEM_UNAVAILABLE // Retry later');
        }
      }
    });
  }
}
