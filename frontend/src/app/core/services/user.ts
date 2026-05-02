import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class UserService {
  private apiUrl = 'http://localhost:8080/api/admin';

  constructor(private http: HttpClient) {}

  getAllUsers() {
    return this.http.get<any[]>(`${this.apiUrl}/users`, { withCredentials: true });
  }

  createUser(dto: any) {
    return this.http.post(`${this.apiUrl}/users`, dto, { withCredentials: true });
  }

  deactivateUser(id: number) {
    return this.http.patch(`${this.apiUrl}/users/${id}/deactivate`, {}, { withCredentials: true });
  }
}
