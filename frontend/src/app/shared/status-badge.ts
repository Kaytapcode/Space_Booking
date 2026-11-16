import type { components } from '../api/schema';

export type BookingStatus = components['schemas']['BookingStatus'];

export interface StatusBadge {
  label: string;
  icon: string;
  /** Pill classes. `--dot` carries the dot colour for the 8px dot element (`bg-(--dot)`). */
  classes: string;
}

const GRAY = 'bg-gray-status-bg text-gray-status-text [--dot:var(--color-gray-status-dot)]';

const BADGES: Record<BookingStatus, StatusBadge> = {
  PENDING: {
    label: 'Chờ duyệt',
    icon: 'clock',
    classes: 'bg-pending-bg text-pending-text [--dot:var(--color-pending-dot)]',
  },
  CONFIRMED: {
    label: 'Đã xác nhận',
    icon: 'calendar-check',
    classes: 'bg-confirmed-bg text-confirmed-text [--dot:var(--color-confirmed-dot)]',
  },
  CHECKED_IN: {
    label: 'Đã check-in',
    icon: 'log-in',
    classes: 'bg-checkedin-bg text-checkedin-text [--dot:var(--color-checkedin-dot)]',
  },
  COMPLETED: {
    label: 'Hoàn thành',
    icon: 'check',
    classes: 'bg-completed-bg text-completed-text [--dot:var(--color-completed-dot)]',
  },
  CANCELLED: { label: 'Đã hủy', icon: 'x', classes: GRAY },
  NO_SHOW: { label: 'Vắng mặt', icon: 'user-x', classes: GRAY },
};

export function statusBadge(status: BookingStatus): StatusBadge {
  return BADGES[status];
}
