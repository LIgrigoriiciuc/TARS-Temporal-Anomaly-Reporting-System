import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string): Observable<any> {
    //without withCredentials: true the HttpOnly cookie isn't sent w the requests
    return this.http.post(`${this.apiUrl}/login`, { email, password }, { withCredentials: true }).pipe(
      tap((response: any) => {
        localStorage.setItem('role', response.role);
        localStorage.setItem('email', response.email);
      })
    );
  }

  logout(): Observable<any> {
    return this.http.post(`${this.apiUrl}/logout`, {}, { withCredentials: true }).pipe(
      tap(() => {
        localStorage.clear();
        this.router.navigate(['/login']);
      })
    );
  }

  getRole(): string | null {
    return localStorage.getItem('role');
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('role');
  }
}
