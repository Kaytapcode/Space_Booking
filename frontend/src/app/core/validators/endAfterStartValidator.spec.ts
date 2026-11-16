import { FormControl, FormGroup } from '@angular/forms';
import { endAfterStartValidator } from './time-range.validator';

function group(start: string | null, end: string | null, sk = 'startTime', ek = 'endTime'): FormGroup {
  return new FormGroup({ [sk]: new FormControl(start), [ek]: new FormControl(end) });
}

describe('endAfterStartValidator', () => {
  const validate = endAfterStartValidator();

  it('flags end before start', () => {
    expect(validate(group('10:00', '09:30'))).toEqual({ endBeforeStart: true });
  });

  it('flags end equal to start', () => {
    expect(validate(group('10:00', '10:00'))).toEqual({ endBeforeStart: true });
  });

  it('accepts end after start', () => {
    expect(validate(group('09:00', '09:30'))).toBeNull();
  });

  it('accepts a range that crosses a digit boundary (09:30 -> 10:00)', () => {
    expect(validate(group('09:30', '10:00'))).toBeNull();
  });

  it.each([
    ['empty start', '', '10:00'],
    ['empty end', '10:00', ''],
    ['both empty', '', ''],
    ['null start', null, '10:00'],
    ['null end', '10:00', null],
  ])('returns null when %s', (_name, start, end) => {
    expect(validate(group(start, end))).toBeNull();
  });

  it('honours custom control keys', () => {
    const custom = endAfterStartValidator('from', 'to');
    expect(custom(group('11:00', '10:00', 'from', 'to'))).toEqual({ endBeforeStart: true });
    expect(custom(group('10:00', '11:00', 'from', 'to'))).toBeNull();
  });

  it('ignores the default keys when custom keys are given', () => {
    const custom = endAfterStartValidator('from', 'to');
    expect(custom(group('11:00', '10:00'))).toBeNull();
  });
});
