import type { components } from '../../api/schema';
import { vnDate, vnTime } from '../../shared/vn-time';

export type RoomSchedule = components['schemas']['RoomScheduleResponse'];
export type ScheduleBooking = components['schemas']['ScheduleBookingResponse'];

// DESIGN.md: 07:00 to 20:00 in 30-minute columns. The API exposes no working-hours config.
export const DAY_START = 7 * 60;
export const DAY_END = 20 * 60;
export const SLOT = 30;
export const COLS = (DAY_END - DAY_START) / SLOT;

export type Cell =
  | { kind: 'free'; col: number; time: string; past: boolean }
  | { kind: 'booking'; col: number; span: number; booking: ScheduleBooking };

export interface RoomRow {
  schedule: RoomSchedule;
  cells: Cell[];
  /** Start times of the next free, not-yet-past slots. */
  freeTimes: string[];
  /** All of the room's bookings by start time, for the card view. */
  bookings: ScheduleBooking[];
}

export function hhmm(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
}

export function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

/** Minutes since midnight of `date` (clamped to the day) for an ISO instant. */
function dayMinutes(iso: string, date: string): number {
  const d = vnDate(iso);
  if (d < date) return 0;
  if (d > date) return 24 * 60;
  return toMinutes(vnTime(iso));
}

const clamp = (n: number) => Math.min(COLS, Math.max(0, n));

/**
 * Turns one room's bookings into an ordered cell list. `nowMin` is the current Ho Chi Minh minute of day
 * when `date` is today, `Infinity` for past dates, `-Infinity` for future dates.
 */
export function buildRow(schedule: RoomSchedule, date: string, nowMin: number): RoomRow {
  const spans = schedule.bookings
    .map((booking) => ({
      booking,
      start: clamp(Math.floor((dayMinutes(booking.startTime, date) - DAY_START) / SLOT)),
      end: clamp(Math.ceil((dayMinutes(booking.endTime, date) - DAY_START) / SLOT)),
    }))
    .sort((a, b) => a.start - b.start);

  const cells: Cell[] = [];
  const free = (col: number): Cell => {
    const start = DAY_START + col * SLOT;
    return { kind: 'free', col, time: hhmm(start), past: start < nowMin };
  };
  let col = 0;
  for (const s of spans) {
    // shortcut: bookings that are not on the 30-minute grid are rounded out; a second one inside the same column is shifted right. Upgrade with minute-level columns if that matters.
    const start = Math.max(s.start, col);
    if (s.end <= start) continue;
    for (; col < start; col++) cells.push(free(col));
    cells.push({ kind: 'booking', col: start, span: s.end - start, booking: s.booking });
    col = s.end;
  }
  for (; col < COLS; col++) cells.push(free(col));

  const freeTimes = cells.flatMap((c) => (c.kind === 'free' && !c.past ? [c.time] : []));
  const bookings = spans.map((s) => s.booking).sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime));
  return { schedule, cells, freeTimes, bookings };
}
