import { Pipe, PipeTransform } from '@angular/core';
import { vnDate, vnTime } from './vn-time';

/** ISO instant -> Ho Chi Minh wall time: `HH:mm` (default) or `YYYY-MM-DD HH:mm` with `'datetime'`. */
@Pipe({ name: 'localTime' })
export class LocalTimePipe implements PipeTransform {
  transform(iso: string | Date | null | undefined, mode: 'time' | 'datetime' = 'time'): string {
    if (iso == null || iso === '') return '';
    return mode === 'datetime' ? `${vnDate(iso)} ${vnTime(iso)}` : vnTime(iso);
  }
}
