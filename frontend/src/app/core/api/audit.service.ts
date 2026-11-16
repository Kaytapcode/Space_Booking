import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import type { components, paths } from '../../api/schema';
import { API_BASE, toParams } from './api-base';

type AuditLogPage = components['schemas']['AuditLogPage'];
export type AuditQuery = NonNullable<paths['/api/v1/audit-logs']['get']['parameters']['query']>;

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);

  search(query?: AuditQuery): Observable<AuditLogPage> {
    return this.http.get<AuditLogPage>(`${API_BASE}/audit-logs`, { params: toParams(query) });
  }
}
