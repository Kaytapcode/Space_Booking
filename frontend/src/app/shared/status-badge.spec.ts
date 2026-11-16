import { BookingStatus, statusBadge } from './status-badge';

// Labels and icons from the status table in docs/design/DESIGN.md.
const TABLE: [BookingStatus, string, string, string][] = [
  ['PENDING', 'Chờ duyệt', 'clock', 'pending'],
  ['CONFIRMED', 'Đã xác nhận', 'calendar-check', 'confirmed'],
  ['CHECKED_IN', 'Đã check-in', 'log-in', 'checkedin'],
  ['COMPLETED', 'Hoàn thành', 'check', 'completed'],
  ['CANCELLED', 'Đã hủy', 'x', 'gray-status'],
  ['NO_SHOW', 'Vắng mặt', 'user-x', 'gray-status'],
];

describe('statusBadge', () => {
  it.each(TABLE)('%s -> label, icon and colour family', (status, label, icon, family) => {
    const badge = statusBadge(status);
    expect(badge.label).toBe(label);
    expect(badge.icon).toBe(icon);
    expect(badge.classes).toContain(`bg-${family}-bg`);
    expect(badge.classes).toContain(`text-${family}-text`);
    expect(badge.classes).toContain(`--color-${family}-dot`);
  });

  it('gives every status a label and an icon (never colour alone)', () => {
    for (const [status] of TABLE) {
      const b = statusBadge(status);
      expect(b.label.length).toBeGreaterThan(0);
      expect(b.icon.length).toBeGreaterThan(0);
    }
  });

  it('CANCELLED and NO_SHOW share gray but differ in label and icon', () => {
    const a = statusBadge('CANCELLED');
    const b = statusBadge('NO_SHOW');
    expect(a.classes).toBe(b.classes);
    expect(a.label).not.toBe(b.label);
    expect(a.icon).not.toBe(b.icon);
  });

  it('uses a distinct colour for each of the four non-gray statuses', () => {
    const classes = (['PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED'] as const).map(
      (s) => statusBadge(s).classes,
    );
    expect(new Set(classes).size).toBe(4);
  });
});
