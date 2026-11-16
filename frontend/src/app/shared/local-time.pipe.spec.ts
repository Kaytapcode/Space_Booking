import { LocalTimePipe } from './local-time.pipe';

describe('LocalTimePipe', () => {
  const pipe = new LocalTimePipe();

  afterEach(() => vi.unstubAllEnvs());

  it('maps a UTC instant to Ho Chi Minh time (UTC+7)', () => {
    expect(pipe.transform('2026-11-16T02:30:00Z')).toBe('09:30');
  });

  it('wraps past midnight into the next day in datetime mode', () => {
    expect(pipe.transform('2026-11-16T18:00:00Z')).toBe('01:00');
    expect(pipe.transform('2026-11-16T18:00:00Z', 'datetime')).toBe('2026-11-17 01:00');
  });

  it('formats datetime mode as YYYY-MM-DD HH:mm', () => {
    expect(pipe.transform('2026-11-16T02:30:00Z', 'datetime')).toBe('2026-11-16 09:30');
  });

  it('accepts an instant with an offset', () => {
    expect(pipe.transform('2026-11-16T09:30:00+07:00')).toBe('09:30');
  });

  it('accepts a Date', () => {
    expect(pipe.transform(new Date('2026-11-16T02:30:00Z'))).toBe('09:30');
  });

  it.each(['UTC', 'America/Los_Angeles', 'Asia/Tokyo', 'Pacific/Auckland'])(
    'is independent of the host time zone (%s)',
    (tz) => {
      vi.stubEnv('TZ', tz);
      expect(pipe.transform('2026-11-16T02:30:00Z')).toBe('09:30');
      expect(pipe.transform('2026-11-16T18:00:00Z', 'datetime')).toBe('2026-11-17 01:00');
    },
  );

  it.each([null, undefined, ''])('returns an empty string for %s', (v) => {
    expect(pipe.transform(v)).toBe('');
    expect(pipe.transform(v, 'datetime')).toBe('');
  });
});
