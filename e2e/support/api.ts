import { APIRequestContext, APIResponse, expect } from '@playwright/test';

export const BACKEND = 'http://localhost:8080';
const V1 = `${BACKEND}/api/v1`;

export const USERS = {
  admin: { email: 'admin@spacebooking.local', password: 'Admin@12345', fullName: 'Quản trị viên' },
  manager: { email: 'manager@spacebooking.local', password: 'Manager@12345', fullName: 'Lê Minh Quân' },
  employee: { email: 'employee@spacebooking.local', password: 'Employee@12345', fullName: 'Trần Thị Bình' },
  employee2: { email: 'employee2@spacebooking.local', password: 'Employee@12345', fullName: 'Phạm Văn Cường' },
} as const;
export type User = (typeof USERS)[keyof typeof USERS];

/** Seed rooms (domain.md section 13) with capacity, as shown in the booking form select. */
export const ROOMS = {
  sen: { name: 'Phòng Sen', capacity: 6 },
  mai: { name: 'Phòng Mai', capacity: 4 },
  cuc: { name: 'Phòng Cúc', capacity: 2 },
  truongSon: { name: 'Phòng Trường Sơn', capacity: 12 },
} as const;

export interface BookingDto {
  id: string;
  title: string;
  startTime: string;
  endTime: string;
  status: string;
  rejectionReason: string | null;
  room: { id: string; name: string };
}

/**
 * Test-support endpoints live outside /api/v1 and exist only under the e2e profile.
 * Reset reseeds with new UUIDs: never hardcode ids, look rooms up with roomIdByName.
 */
export async function resetBackend(request: APIRequestContext): Promise<void> {
  const res = await request.post(`${BACKEND}/test-support/reset`);
  expect(res.status(), 'test-support reset').toBe(204);
}

export async function setBackendClock(request: APIRequestContext, instant: Date): Promise<void> {
  const res = await request.post(`${BACKEND}/test-support/clock`, { data: { instant: instant.toISOString() } });
  expect(res.status(), 'test-support clock').toBe(200);
}

export async function runScheduler(request: APIRequestContext): Promise<void> {
  const res = await request.post(`${BACKEND}/test-support/run-scheduler`);
  expect(res.status(), 'test-support run-scheduler').toBe(204);
}

export async function tokenFor(request: APIRequestContext, user: User): Promise<string> {
  const res = await request.post(`${V1}/auth/login`, { data: { email: user.email, password: user.password } });
  expect(res.status(), `login ${user.email}`).toBe(200);
  return ((await res.json()) as { accessToken: string }).accessToken;
}

/** Creates a second employee for multi-user scenarios; the account is not what those scenarios test. */
export async function registerUser(request: APIRequestContext, user: User): Promise<void> {
  const res = await request.post(`${V1}/auth/register`, {
    data: { email: user.email, password: user.password, fullName: user.fullName },
  });
  expect(res.status(), `register ${user.email}`).toBe(201);
}

export async function apiGet<T>(request: APIRequestContext, token: string, path: string): Promise<T> {
  const res = await request.get(`${V1}${path}`, { headers: { Authorization: `Bearer ${token}` } });
  expect(res.status(), `GET ${path}`).toBe(200);
  return (await res.json()) as T;
}

export async function apiSend(
  request: APIRequestContext,
  token: string,
  method: 'post' | 'put' | 'patch' | 'delete',
  path: string,
  data?: unknown,
): Promise<APIResponse> {
  return request[method](`${V1}${path}`, { headers: { Authorization: `Bearer ${token}` }, data });
}

/** Arranged data: books a room over the API and returns the new booking id. */
export async function createBooking(
  request: APIRequestContext,
  token: string,
  booking: { roomName: string; title: string; start: Date; end: Date },
): Promise<string> {
  const res = await apiSend(request, token, 'post', '/bookings', {
    roomId: await roomIdByName(request, token, booking.roomName),
    title: booking.title,
    startTime: booking.start.toISOString(),
    endTime: booking.end.toISOString(),
  });
  expect(res.status(), `create booking "${booking.title}"`).toBe(201);
  return ((await res.json()) as { id: string }).id;
}

export async function myBookings(request: APIRequestContext, token: string): Promise<BookingDto[]> {
  return (await apiGet<{ content: BookingDto[] }>(request, token, '/bookings/my-bookings?size=50')).content;
}

export async function bookingByTitle(request: APIRequestContext, token: string, title: string): Promise<BookingDto> {
  const found = (await myBookings(request, token)).filter((b) => b.title === title);
  expect(found, `booking titled "${title}"`).toHaveLength(1);
  return found[0];
}

export async function roomIdByName(request: APIRequestContext, token: string, name: string): Promise<string> {
  const rooms = await apiGet<{ id: string; name: string }[]>(request, token, '/rooms');
  const room = rooms.find((r) => r.name === name);
  expect(room, `room ${name}`).toBeDefined();
  return room!.id;
}
