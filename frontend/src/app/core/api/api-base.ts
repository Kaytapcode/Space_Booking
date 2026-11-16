import { HttpParams } from '@angular/common/http';

export const API_BASE = '/api/v1';

/** Drops empty values; arrays become comma-separated (the contract's `explode: false`). */
export function toParams(query: object | undefined): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value) && value.length === 0) continue;
    params = params.set(key, Array.isArray(value) ? value.join(',') : String(value));
  }
  return params;
}
