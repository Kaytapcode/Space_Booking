import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import type { components, paths } from '../../api/schema';
import { API_BASE, toParams } from './api-base';

type BookingResponse = components['schemas']['BookingResponse'];
type BookingPage = components['schemas']['BookingPage'];
type BookingRequest = components['schemas']['BookingRequest'];
type DailyScheduleResponse = components['schemas']['DailyScheduleResponse'];
export type MyBookingsQuery = NonNullable<paths['/api/v1/bookings/my-bookings']['get']['parameters']['query']>;
export type PendingQuery = NonNullable<paths['/api/v1/bookings/pending']['get']['parameters']['query']>;

@Injectable({ providedIn: 'root' })
export class BookingsApi {
  private readonly http = inject(HttpClient);

  create(body: BookingRequest): Observable<BookingResponse> {
    return this.http.post<BookingResponse>(`${API_BASE}/bookings`, body);
  }

  dailySchedule(date: string): Observable<DailyScheduleResponse> {
    return this.http.get<DailyScheduleResponse>(`${API_BASE}/bookings/daily-schedule`, {
      params: toParams({ date }),
    });
  }

  myBookings(query?: MyBookingsQuery): Observable<BookingPage> {
    return this.http.get<BookingPage>(`${API_BASE}/bookings/my-bookings`, { params: toParams(query) });
  }

  pending(query?: PendingQuery): Observable<BookingPage> {
    return this.http.get<BookingPage>(`${API_BASE}/bookings/pending`, { params: toParams(query) });
  }

  approve(id: string): Observable<BookingResponse> {
    return this.transition(id, 'approve');
  }

  reject(id: string, reason: string): Observable<BookingResponse> {
    return this.http.patch<BookingResponse>(`${API_BASE}/bookings/${id}/reject`, { reason });
  }

  checkIn(id: string): Observable<BookingResponse> {
    return this.transition(id, 'check-in');
  }

  checkOut(id: string): Observable<BookingResponse> {
    return this.transition(id, 'checkout');
  }

  cancel(id: string): Observable<BookingResponse> {
    return this.transition(id, 'cancel');
  }

  private transition(id: string, action: string): Observable<BookingResponse> {
    return this.http.patch<BookingResponse>(`${API_BASE}/bookings/${id}/${action}`, null);
  }
}
