import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  inject,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/http/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    // Restore the profile of a stored session before the first navigation so guards can read the role.
    // A failed call is already handled by the interceptor (401 clears the session).
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      return auth.token() ? firstValueFrom(auth.loadMe().pipe(catchError(() => of(null)))) : undefined;
    }),
  ],
};
