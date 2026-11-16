import { HttpErrorResponse } from '@angular/common/http';
import { problemMessage } from './problem-messages';

// Mirrors the ErrorCode enum in docs/contract/openapi.yaml.
const CODES = [
  'VALIDATION_FAILED', 'START_IN_PAST', 'DURATION_EXCEEDED', 'UNAUTHENTICATED', 'INVALID_CREDENTIALS',
  'ACCESS_DENIED', 'ROOM_NOT_FOUND', 'BOOKING_NOT_FOUND', 'EMAIL_ALREADY_EXISTS',
  'ROOM_NAME_ALREADY_EXISTS', 'ROOM_HAS_FUTURE_BOOKINGS', 'CONCURRENT_UPDATE', 'BOOKING_CONFLICT',
  'INVALID_STATE_TRANSITION', 'BOOKING_ALREADY_STARTED', 'OUTSIDE_CHECKIN_WINDOW', 'INTERNAL_ERROR',
  'REQUEST_NOT_SUPPORTED',
] as const;

const SERVER_DETAIL = 'Server English detail that must never be shown.';

function http(status: number, code?: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status,
    error: code === undefined ? null : { type: 'about:blank', title: 'T', status, detail: SERVER_DETAIL, code },
  });
}

// Accented Vietnamese letters; every UI message must contain at least one (keeps English text out).
const VIETNAMESE = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i;

describe('problemMessage', () => {
  it.each(CODES)('maps %s to a non-empty Vietnamese message that is not the server detail', (code) => {
    const msg = problemMessage(http(409, code));
    expect(msg.trim().length).toBeGreaterThan(0);
    expect(msg).toMatch(VIETNAMESE);
    expect(msg).not.toBe(SERVER_DETAIL);
    expect(msg).not.toContain(SERVER_DETAIL);
  });

  it.each([
    [400, 'unknown'],
    [401, 'unknown'],
    [403, 'unknown'],
    [404, 'unknown'],
    [409, 'unknown'],
    [500, 'unknown'],
    [503, 'unknown'],
  ])('falls back by status %i for an unknown code', (status, code) => {
    const msg = problemMessage(http(status, code));
    expect(msg).toMatch(VIETNAMESE);
    expect(msg).not.toContain(SERVER_DETAIL);
  });

  it('falls back by status when the body has no code', () => {
    const msg = problemMessage(http(500));
    expect(msg).toMatch(VIETNAMESE);
  });

  it('returns a network message for status 0', () => {
    const msg = problemMessage(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }));
    expect(msg).toMatch(VIETNAMESE);
    expect(msg).not.toBe(problemMessage(http(500, 'x')));
  });

  it('returns a Vietnamese generic message for non-HTTP errors', () => {
    for (const err of [new Error('boom'), 'boom', null, undefined, 42]) {
      const msg = problemMessage(err);
      expect(msg).toMatch(VIETNAMESE);
      expect(msg).not.toContain('boom');
    }
  });
});
