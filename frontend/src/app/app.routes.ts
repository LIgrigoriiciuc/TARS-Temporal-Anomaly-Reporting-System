import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./features/auth/login/login').then(m => m.Login) },
  { path: 'supervisor', loadComponent: () => import('./features/supervisor/dashboard/dashboard').then(m => m.Dashboard), canActivate: [authGuard] },
  { path: 'agent', loadComponent: () => import('./features/agent/dashboard/dashboard').then(m => m.Dashboard), canActivate: [authGuard] },
  { path: '**', redirectTo: 'login' }
];
