import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TOKEN_KEY } from '../auth/auth.service';
import { ToastService } from '../ui/toast.service';
import { authInterceptor } from './auth.interceptor';
import { problemMessage } from './problem-messages';

const problem = (status: number, code: string) => ({
  type: 'about:blank',
  title: 'T',
  status,
  detail: 'Server English detail.',
  code,
});

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let toast: ToastService;
  let router: Router;

  function setup(token: string | null): void {
    localStorage.clear();
    if (token) localStorage.setItem(TOKEN_KEY, token);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    toast = TestBed.inject(ToastService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  }

  afterEach(() => {
    ctrl.verify();
    localStorage.clear();
  });

  it('adds the Bearer token when a token exists', () => {
    setup('abc.def.ghi');
    http.get('/api/v1/rooms').subscribe();
    const req = ctrl.expectOne('/api/v1/rooms');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    req.flush([]);
  });

  it.each(['/api/v1/auth/login', '/api/v1/auth/register'])('does not add the token to %s', (url) => {
    setup('abc.def.ghi');
    http.post(url, {}).subscribe();
    const req = ctrl.expectOne(url);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does not send the token to a non-API URL', () => {
    setup('abc.def.ghi');
    http.get('https://cdn.example.test/x.json').subscribe();
    const req = ctrl.expectOne('https://cdn.example.test/x.json');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('adds no header without a token', () => {
    setup(null);
    http.get('/api/v1/rooms').subscribe();
    const req = ctrl.expectOne('/api/v1/rooms');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('on 401 clears the session and navigates to /login', () => {
    setup('abc.def.ghi');
    http.get('/api/v1/bookings/my-bookings').subscribe({ error: () => undefined });
    ctrl
      .expectOne('/api/v1/bookings/my-bookings')
      .flush(problem(401, 'UNAUTHENTICATED'), { status: 401, statusText: 'Unauthorized' });
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('on 409 BOOKING_CONFLICT shows the mapped message and keeps the session', () => {
    setup('abc.def.ghi');
    const spy = vi.spyOn(toast, 'error');
    http.post('/api/v1/bookings', {}).subscribe({ error: () => undefined });
    ctrl
      .expectOne('/api/v1/bookings')
      .flush(problem(409, 'BOOKING_CONFLICT'), { status: 409, statusText: 'Conflict' });
    expect(spy).toHaveBeenCalledTimes(1);
    const shown = spy.mock.calls[0][0];
    expect(shown).toMatch(/[ăâđêôơư]|[áàảãạéèíìóòúùý]/i);
    expect(shown).not.toContain('Server English detail.');
    expect(toast.toasts().map((t) => t.message)).toContain(shown);
    expect(localStorage.getItem(TOKEN_KEY)).toBe('abc.def.ghi');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('rethrows the HttpErrorResponse with the ProblemDetail body while the toast shows only the mapped text', () => {
    setup('abc.def.ghi');
    const spy = vi.spyOn(toast, 'error');
    let received: HttpErrorResponse | undefined;
    http.post('/api/v1/bookings', {}).subscribe({ error: (e) => (received = e) });
    ctrl
      .expectOne('/api/v1/bookings')
      .flush(problem(409, 'BOOKING_CONFLICT'), { status: 409, statusText: 'Conflict' });
    expect(received).toBeInstanceOf(HttpErrorResponse);
    expect(received?.status).toBe(409);
    expect(received?.error.code).toBe('BOOKING_CONFLICT');
    expect(received?.error.detail).toBe('Server English detail.');
    expect(spy).toHaveBeenCalledWith(problemMessage(received));
    expect(spy.mock.calls[0][0]).not.toContain('Server English detail.');
  });

  it('shows the same text as problemMessage for the error', () => {
    setup('abc.def.ghi');
    const spy = vi.spyOn(toast, 'error');
    let err: unknown;
    http.get('/api/v1/rooms').subscribe({ error: (e) => (err = e) });
    ctrl.expectOne('/api/v1/rooms').flush(problem(403, 'ACCESS_DENIED'), {
      status: 403,
      statusText: 'Forbidden',
    });
    expect(spy).toHaveBeenCalledWith(problemMessage(err));
  });

  it('does not toast on success', () => {
    setup('abc.def.ghi');
    const spy = vi.spyOn(toast, 'error');
    http.get('/api/v1/rooms').subscribe();
    ctrl.expectOne('/api/v1/rooms').flush([]);
    expect(spy).not.toHaveBeenCalled();
  });
});
