import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import type { components } from '../../api/schema';
import { API_BASE } from '../api/api-base';

type AuthResponse = components['schemas']['AuthResponse'];
type UserResponse = components['schemas']['UserResponse'];
type LoginRequest = components['schemas']['LoginRequest'];
type RegisterRequest = components['schemas']['RegisterRequest'];
export type Role = components['schemas']['Role'];

export const TOKEN_KEY = 'sb.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokenState = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly userState = signal<UserResponse | null>(null);

  readonly token = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly role = computed<Role | null>(() => this.userState()?.role ?? null);

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_BASE}/auth/login`, body).pipe(tap((r) => this.start(r)));
  }

  register(body: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_BASE}/auth/register`, body).pipe(tap((r) => this.start(r)));
  }

  loadMe(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${API_BASE}/auth/me`).pipe(tap((u) => this.userState.set(u)));
  }

  /** Clears the session and goes to the login page. */
  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.tokenState.set(null);
    this.userState.set(null);
    void this.router.navigate(['/login']);
  }

  private start(r: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, r.accessToken);
    this.tokenState.set(r.accessToken);
    this.userState.set(r.user);
  }
}
