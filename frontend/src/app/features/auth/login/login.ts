import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, CommonModule],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login {
  email = '';
  password = '';
  errorMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  onLogin() {
    //delete old cookie to prevent conflicts with new login
    document.cookie = 'jwt=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
    this.authService.login(this.email, this.password).subscribe({
      next: (response) => {
        const role = response.role;
        if (role === 'Supervisor') {
          this.router.navigate(['/supervisor']);
        } else {
          this.router.navigate(['/agent']);
        }
      },
      error: (err) => {
        if (err.status === 403) {
          this.errorMessage = 'ACCOUNT_TERMINATED // Access denied';
        } else {
          this.errorMessage = 'Invalid credentials';
        }
      }
    });
  }
}
