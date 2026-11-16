import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from './auth.service';

// The user profile is loaded before routing starts (see app.config.ts), so guards stay synchronous.
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token() ? true : inject(Router).parseUrl('/login');
};

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.token()) return router.parseUrl('/login');
    const role = auth.role();
    return role !== null && roles.includes(role) ? true : router.parseUrl('/');
  };
}
