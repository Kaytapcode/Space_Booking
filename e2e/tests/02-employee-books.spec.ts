import { USERS, bookingByTitle, tokenFor } from '../support/api';
import { expect, test, wall } from '../support/fixtures';
import { bookingItem, fillBooking, loginUi, submitBooking, successToast } from '../support/ui';

test('2. employee books a normal room from the dashboard and sees CONFIRMED', async ({ page, request }) => {
  const title = 'Họp nhóm sản phẩm';
  await loginUi(page, USERS.employee);
  await expect(page.getByText('Phòng Sen').first()).toBeVisible();

  // One tap/click on a free slot opens the booking form prefilled with room, date and times.
  await page.getByRole('button', { name: 'Đặt Phòng Sen lúc 09:00', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Đặt phòng' })).toBeVisible();
  await expect(page.getByLabel('Giờ bắt đầu')).toHaveValue('09:00');
  await expect(page.getByLabel('Giờ kết thúc')).toHaveValue('09:30');
  await expect(page.getByRole('option', { name: 'Phòng Sen · 6 chỗ', selected: true })).toBeAttached();

  await fillBooking(page, { room: 'sen', title, start: '09:00', end: '10:00' });
  await submitBooking(page);

  await expect(successToast(page, 'Đã đặt phòng.')).toBeVisible();
  await expect(page).toHaveURL(/\/my-bookings$/);
  const item = bookingItem(page, title);
  await expect(item).toContainText('Phòng Sen');
  await expect(item).toContainText('Đã xác nhận');

  const booking = await bookingByTitle(request, await tokenFor(request, USERS.employee), title);
  expect(booking.status).toBe('CONFIRMED');
  expect(booking.room.name).toBe('Phòng Sen');
  expect(new Date(booking.startTime).getTime()).toBe(wall('09:00').getTime());
  expect(new Date(booking.endTime).getTime()).toBe(wall('10:00').getTime());
});
