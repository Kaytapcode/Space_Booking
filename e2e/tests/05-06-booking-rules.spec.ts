import { USERS, bookingByTitle, myBookings, registerUser, tokenFor } from '../support/api';
import { desktopOnly, expect, test, wall } from '../support/fixtures';
import { bookViaUi, errorToast, fillBooking, loginUi, openBookingForm, submitBooking, successToast } from '../support/ui';

desktopOnly('booking rules are viewport-independent; mobile layout is covered by scenario 12');

const CONFLICT = 'Khung giờ này đã có người đặt. Chọn giờ khác.';

test('5. two contexts book the same slot: one wins, the other gets BOOKING_CONFLICT, adjacent slot works', async ({
  page,
  request,
  world,
}) => {
  await registerUser(request, USERS.employee2);
  const first = page;
  const second = await world.newPage();
  await loginUi(first, USERS.employee);
  await loginUi(second, USERS.employee2);

  // Both forms are filled before either submits, so the second submit hits an already taken slot.
  await openBookingForm(first);
  await openBookingForm(second);
  await fillBooking(first, { room: 'sen', title: 'Slot của người thứ nhất', start: '09:00', end: '10:00' });
  await fillBooking(second, { room: 'sen', title: 'Slot của người thứ hai', start: '09:00', end: '10:00' });

  await submitBooking(first);
  await expect(successToast(first, 'Đã đặt phòng.')).toBeVisible();
  await expect(first).toHaveURL(/\/my-bookings$/);

  await submitBooking(second);
  await expect(errorToast(second, CONFLICT)).toBeVisible();
  await expect(second).toHaveURL(/\/bookings\/new$/);
  await expect(second.getByLabel('Giờ bắt đầu')).toHaveAttribute('aria-invalid', 'true');

  // Adjacent slot [10:00, 11:00) does not overlap [09:00, 10:00).
  await second.getByLabel('Giờ bắt đầu').fill('10:00');
  await second.getByLabel('Giờ kết thúc').fill('11:00');
  await submitBooking(second);
  await expect(successToast(second, 'Đã đặt phòng.')).toBeVisible();
  await expect(second).toHaveURL(/\/my-bookings$/);

  const winner = await bookingByTitle(request, await tokenFor(request, USERS.employee), 'Slot của người thứ nhất');
  expect(winner.status).toBe('CONFIRMED');
  const loserBookings = await myBookings(request, await tokenFor(request, USERS.employee2));
  expect(loserBookings).toHaveLength(1);
  expect(new Date(loserBookings[0].startTime).getTime()).toBe(wall('10:00').getTime());
  expect(loserBookings[0].status).toBe('CONFIRMED');
});

test('6. employee is blocked at 2h01m, manager books the same duration', async ({ page, request, world }) => {
  const title = 'Hội thảo dài';
  await loginUi(page, USERS.employee);
  await openBookingForm(page);
  await expect(page.getByText('Nhân viên đặt tối đa 2 giờ mỗi lượt.')).toBeVisible();
  await fillBooking(page, { room: 'sen', title, start: '14:00', end: '16:01' });
  await submitBooking(page);
  await expect(page.getByText('Rút ngắn thời gian.')).toBeVisible();
  await expect(page.getByLabel('Giờ kết thúc')).toHaveAttribute('aria-invalid', 'true');
  await expect(page).toHaveURL(/\/bookings\/new$/);
  expect(await myBookings(request, await tokenFor(request, USERS.employee))).toHaveLength(0);

  // Exactly 2h is allowed for an employee.
  await page.getByLabel('Giờ kết thúc').fill('16:00');
  await expect(page.getByText('Rút ngắn thời gian.')).toHaveCount(0);

  const manager = await world.newPage();
  await loginUi(manager, USERS.manager);
  await openBookingForm(manager);
  await expect(manager.getByText('Nhân viên đặt tối đa 2 giờ mỗi lượt.')).toHaveCount(0);
  await bookViaUi(manager, { room: 'sen', title, start: '14:00', end: '16:01' });
  await expect(successToast(manager, 'Đã đặt phòng.')).toBeVisible();

  const booking = await bookingByTitle(request, await tokenFor(request, USERS.manager), title);
  expect(booking.status).toBe('CONFIRMED');
  expect(new Date(booking.endTime).getTime() - new Date(booking.startTime).getTime()).toBe(121 * 60_000);
});
