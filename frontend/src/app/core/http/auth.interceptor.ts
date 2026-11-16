import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../ui/toast.service';
import { problemMessage } from './problem-messages';
import { API_BASE } from '../api/api-base';

const PUBLIC_PATHS = ['/auth/login', '/auth/register'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);

  const token = auth.token();
  const isPublic = PUBLIC_PATHS.some((p) => req.url.includes(p));
  // Only our own API gets the token; any other URL would leak it.
  const outgoing =
    token && !isPublic && req.url.startsWith(API_BASE) ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(outgoing).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) auth.logout();
      toast.error(problemMessage(err));
      return throwError(() => err);
    }),
  );
};
