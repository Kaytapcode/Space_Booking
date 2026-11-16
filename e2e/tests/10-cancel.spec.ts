import { USERS, apiSend, bookingByTitle, createBooking, registerUser, tokenFor } from '../support/api';
import { desktopOnly, expect, test, wall } from '../support/fixtures';
import { bookViaUi, bookingItem, goTo, loginUi, successToast } from '../support/ui';

desktopOnly('cancel rules are viewport-independent; mobile layout is covered by scenario 12');

test('10. owner cancels own booking; nobody else can cancel it (no button, API answers 403)', async ({ page, request }) => {
  const own = 'Họp tôi tự hủy';
  const foreign = 'Họp của người khác';
  await registerUser(request, USERS.employee2);
  const otherToken = await tokenFor(request, USERS.employee2);
  // Arranged data: the other employee's booking is not what this scenario drives through the UI.
  const foreignId = await createBooking(request, otherToken, {
    roomName: 'Phòng Sen',
    title: foreign,
    start: wall('11:00'),
    end: wall('12:00'),
  });

  await loginUi(page, USERS.employee);
  await bookViaUi(page, { room: 'mai', title: own, start: '11:00', end: '12:00' });

  await page.getByRole('button', { name: `Hủy lịch ${own}` }).click();
  const dialog = page.getByRole('dialog', { name: 'Hủy lịch đặt' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Hủy lịch', exact: true }).click();
  await expect(successToast(page, 'Đã hủy lịch đặt.')).toBeVisible();
  await expect(bookingItem(page, own)).toContainText('Đã hủy');
  await expect(page.getByRole('button', { name: `Hủy lịch ${own}` })).toHaveCount(0);

  const token = await tokenFor(request, USERS.employee);
  expect((await bookingByTitle(request, token, own)).status).toBe('CANCELLED');

  // The other employee's booking is absent from My bookings and has no cancel button on the dashboard.
  await expect(page.getByText(foreign)).toHaveCount(0);
  await goTo(page, 'Lịch tổng quan', 'Lịch tổng quan');
  await page.getByRole('button', { name: new RegExp(foreign) }).click();
  const detail = page.getByRole('dialog', { name: 'Chi tiết lịch đặt' });
  await expect(detail).toContainText(USERS.employee2.fullName);
  await expect(detail.getByRole('button', { name: /Hủy/ })).toHaveCount(0);
  await detail.getByRole('button', { name: 'Đóng' }).click();
  await expect(detail).toHaveCount(0);

  const refused = await apiSend(request, token, 'patch', `/bookings/${foreignId}/cancel`);
  expect(refused.status()).toBe(403);
  expect(((await refused.json()) as { code: string }).code).toBe('ACCESS_DENIED');
  expect((await bookingByTitle(request, otherToken, foreign)).status).toBe('CONFIRMED');
});
