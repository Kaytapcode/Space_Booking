// P5-3: Asia/Ho_Chi_Minh is a fixed UTC+7 (no DST), so wall time <-> UTC is plain arithmetic.
const TZ = 'Asia/Ho_Chi_Minh';
const OFFSET = '+07:00';

const timeFmt = new Intl.DateTimeFormat('en-GB', {
  timeZone: TZ,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const dateFmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: TZ,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** ISO instant -> `HH:mm` in Ho Chi Minh time. */
export function vnTime(iso: string | Date): string {
  return timeFmt.format(new Date(iso));
}

/** ISO instant -> `YYYY-MM-DD` in Ho Chi Minh time. */
export function vnDate(iso: string | Date): string {
  return dateFmt.format(new Date(iso));
}

export function vnToday(): string {
  return vnDate(new Date());
}

/** `YYYY-MM-DD` + `HH:mm` (Ho Chi Minh wall time) -> UTC ISO-8601 for the API. */
export function toUtcIso(date: string, time: string): string {
  return new Date(`${date}T${time}:00${OFFSET}`).toISOString();
}

/** Shifts a `YYYY-MM-DD` by whole days. */
export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00${OFFSET}`);
  d.setUTCDate(d.getUTCDate() + days);
  return vnDate(d);
}
