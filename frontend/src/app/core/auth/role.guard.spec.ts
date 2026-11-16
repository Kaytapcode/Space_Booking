import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authGuard, roleGuard } from './auth.guard';
import { AuthService, Role, TOKEN_KEY } from './auth.service';

const ROLES: Role[] = ['EMPLOYEE', 'MANAGER', 'ADMIN'];

describe('route guards (docs/contract/domain.md section 9)', () => {
  let router: Router;

  /** Logs in through the real AuthService so token() and role() are both set. */
  function loginAs(role: Role | null): void {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    router = TestBed.inject(Router);
    if (role === null) return;
    const auth = TestBed.inject(AuthService);
    auth.login({ email: 'u@example.com', password: 'password1' }).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url.endsWith('/auth/login'))
      .flush({
        accessToken: 'tok',
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: { id: '00000000-0000-4000-8000-000000000001', email: 'u@example.com', fullName: 'U', role },
      });
  }

  function run(guard: CanActivateFn): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  const expectRedirect = (result: boolean | UrlTree, path: string): void => {
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe(path);
  };

  afterEach(() => localStorage.removeItem(TOKEN_KEY));

  describe('authGuard', () => {
    it('redirects to /login without a token', () => {
      loginAs(null);
      expectRedirect(run(authGuard), '/login');
    });

    it.each(ROLES)('allows %s with a token', (role) => {
      loginAs(role);
      expect(run(authGuard)).toBe(true);
    });
  });

  describe('roleGuard', () => {
    it.each([['MANAGER'], ['ADMIN'], ['EMPLOYEE']] as [Role][])(
      'redirects to /login without a token (guard for %s)',
      (required) => {
        loginAs(null);
        expectRedirect(run(roleGuard(required)), '/login');
      },
    );

    // Allowed roles per route, from the permission matrix.
    const matrix: [string, Role[], Role[]][] = [
      ['MANAGER-only (GET /bookings/pending, approve/reject)', ['MANAGER'], ['MANAGER']],
      ['ADMIN-only (rooms CRUD, audit-logs)', ['ADMIN'], ['ADMIN']],
      ['book/my-bookings (EMPLOYEE, MANAGER; ADMIN gets 403)', ['EMPLOYEE', 'MANAGER'], ['EMPLOYEE', 'MANAGER']],
    ];

    describe.each(matrix)('%s', (_name, guardRoles, allowed) => {
      it.each(ROLES)('role %s', (role) => {
        loginAs(role);
        const result = run(roleGuard(...guardRoles));
        if (allowed.includes(role)) expect(result).toBe(true);
        else expectRedirect(result, '/');
      });
    });

    it('MANAGER-only does not let ADMIN in (no inheritance)', () => {
      loginAs('ADMIN');
      expectRedirect(run(roleGuard('MANAGER')), '/');
    });

    it('ADMIN-only does not let MANAGER in (no inheritance)', () => {
      loginAs('MANAGER');
      expectRedirect(run(roleGuard('ADMIN')), '/');
    });

    it('redirects to / when the role is unknown but a token exists', () => {
      localStorage.setItem(TOKEN_KEY, 'tok');
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      router = TestBed.inject(Router);
      expectRedirect(run(roleGuard('EMPLOYEE', 'MANAGER', 'ADMIN')), '/');
    });
  });
});
