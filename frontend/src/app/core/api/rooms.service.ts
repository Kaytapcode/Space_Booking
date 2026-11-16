import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import type { components, paths } from '../../api/schema';
import { API_BASE, toParams } from './api-base';

type RoomResponse = components['schemas']['RoomResponse'];
type RoomRequest = components['schemas']['RoomRequest'];
export type RoomSearch = NonNullable<paths['/api/v1/rooms']['get']['parameters']['query']>;

@Injectable({ providedIn: 'root' })
export class RoomsApi {
  private readonly http = inject(HttpClient);

  search(query?: RoomSearch): Observable<RoomResponse[]> {
    return this.http.get<RoomResponse[]>(`${API_BASE}/rooms`, { params: toParams(query) });
  }

  create(body: RoomRequest): Observable<RoomResponse> {
    return this.http.post<RoomResponse>(`${API_BASE}/rooms`, body);
  }

  update(roomId: string, body: RoomRequest): Observable<RoomResponse> {
    return this.http.put<RoomResponse>(`${API_BASE}/rooms/${roomId}`, body);
  }

  deactivate(roomId: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/rooms/${roomId}`);
  }
}
