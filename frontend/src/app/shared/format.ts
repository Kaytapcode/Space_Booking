import { AbstractControl, ValidationErrors } from '@angular/forms';
import { vnDate, vnTime } from './vn-time';

/** `YYYY-MM-DD` -> `dd/MM/yyyy`. */
export function displayDate(date: string): string {
  return `${date.slice(8, 10)}/${date.slice(5, 7)}/${date.slice(0, 4)}`;
}

/** ISO instant -> `dd/MM/yyyy` in Ho Chi Minh time. */
export function vnDisplayDate(iso: string): string {
  return displayDate(vnDate(iso));
}

/** `09:00-10:30` in Ho Chi Minh time. */
export function timeRange(startIso: string, endIso: string): string {
  return `${vnTime(startIso)}-${vnTime(endIso)}`;
}

/** First matching message of a touched or dirty control, else null. */
export function controlError(control: AbstractControl, messages: Record<string, string>): string | null {
  if (!control.errors || !(control.touched || control.dirty)) return null;
  const key = Object.keys(control.errors).find((k) => k in messages);
  return key ? messages[key] : null;
}

/** Rejects whitespace-only text (the API treats blank-after-trim as invalid). */
export function notBlank(control: AbstractControl): ValidationErrors | null {
  return String(control.value ?? '').trim() ? null : { blank: true };
}
