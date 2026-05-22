import { Router, Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { inject } from '@angular/core';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then(m => m.Login),
    canActivate: [() => {
      const role = localStorage.getItem('role');
      if (role === 'Supervisor') { inject(Router).navigate(['/supervisor']); return false; }
      if (role === 'Agent')      { inject(Router).navigate(['/agent']);      return false; }
      return true;
    }]
  },
  {
    path: 'supervisor',
    loadComponent: () => import('./features/supervisor/dashboard/dashboard').then(m => m.Dashboard),
    canActivate: [authGuard]
  },
  {
    path: 'agent',
    loadComponent: () => import('./features/agent/dashboard/dashboard').then(m => m.Dashboard),
    canActivate: [authGuard]
  },
  {
    path: 'graph',
    loadComponent: () => import('./features/graph/graph').then(m => m.GraphPage),
    canActivate: [authGuard]
  },
  {
    path: 'subscription',
    loadComponent: () => import('./features/subscription/subscription').then(m => m.SubscriptionPage),
    canActivate: [authGuard]
  },
  { path: '**', redirectTo: 'login' }, // always last
];
