import { USERS, bookingByTitle, registerUser, runScheduler, tokenFor } from '../support/api';
import { desktopOnly, expect, test, wall } from '../support/fixtures';
import { bookViaUi, bookingItem, errorToast, loginUi, successToast } from '../support/ui';

desktopOnly('time-driven flows are viewport-independent; mobile layout is covered by scenario 12');

test('7. check-in works inside the window and is refused outside it', async ({ page, request, world }) => {
  const inside = 'Họp trong khung check-in';
  const outside = 'Họp quá sớm để check-in';
  await loginUi(page, USERS.employee);
  await bookViaUi(page, { room: 'sen', title: inside, start: '14:00', end: '15:00' });
  await bookViaUi(page, { room: 'mai', title: outside, start: '17:00', end: '18:00' });

  await world.setNow('13:50'); // start - 10 minutes
  await page.reload();

  await page.getByRole('button', { name: `Check-in lịch ${inside}` }).click();
  await expect(successToast(page, 'Đã check-in.')).toBeVisible();
  await expect(bookingItem(page, inside)).toContainText('Đã check-in');

  await page.getByRole('button', { name: `Check-in lịch ${outside}` }).click();
  await expect(errorToast(page, 'Chỉ check-in từ 15 phút trước đến 15 phút sau giờ bắt đầu.')).toBeVisible();
  await expect(bookingItem(page, outside)).toContainText('Đã xác nhận');

  const token = await tokenFor(request, USERS.employee);
  expect((await bookingByTitle(request, token, inside)).status).toBe('CHECKED_IN');
  expect((await bookingByTitle(request, token, outside)).status).toBe('CONFIRMED');
});

test('8. early checkout frees the remainder for another user', async ({ page, request, world }) => {
  const title = 'Họp ngắn hơn dự kiến';
  const followUp = 'Họp nhận phòng trả sớm';
  await registerUser(request, USERS.employee2);
  await loginUi(page, USERS.employee);
  await bookViaUi(page, { room: 'sen', title, start: '14:00', end: '16:00' });

  await world.setNow('14:00');
  await page.reload();
  await page.getByRole('button', { name: `Check-in lịch ${title}` }).click();
  await expect(successToast(page, 'Đã check-in.')).toBeVisible();

  await world.setNow('14:30');
  await page.reload();
  await page.getByRole('button', { name: `Checkout lịch ${title}` }).click();
  await expect(successToast(page, 'Đã checkout. Phòng được trả sớm.')).toBeVisible();
  await expect(bookingItem(page, title)).toContainText('Hoàn thành');

  const owner = await bookingByTitle(request, await tokenFor(request, USERS.employee), title);
  expect(owner.status).toBe('COMPLETED');
  expect(new Date(owner.endTime).getTime()).toBe(wall('14:30').getTime());

  const other = await world.newPage();
  await loginUi(other, USERS.employee2);
  await bookViaUi(other, { room: 'sen', title: followUp, start: '14:30', end: '16:00' });
  await expect(successToast(other, 'Đã đặt phòng.')).toBeVisible();
  const freed = await bookingByTitle(request, await tokenFor(request, USERS.employee2), followUp);
  expect(freed.status).toBe('CONFIRMED');
});

test('9. a booking not checked in 16 minutes after start becomes NO_SHOW and the slot reopens', async ({
  page,
  request,
  world,
}) => {
  const title = 'Họp không ai đến';
  const reopened = 'Họp dùng lại phòng';
  await loginUi(page, USERS.employee);
  await bookViaUi(page, { room: 'sen', title, start: '14:00', end: '15:00' });

  await world.setNow('14:16');
  await runScheduler(request);
  await page.reload();
  await expect(bookingItem(page, title)).toContainText('Vắng mặt');
  expect((await bookingByTitle(request, await tokenFor(request, USERS.employee), title)).status).toBe('NO_SHOW');

  const manager = await world.newPage();
  await loginUi(manager, USERS.manager);
  await bookViaUi(manager, { room: 'sen', title: reopened, start: '14:30', end: '15:00' });
  await expect(successToast(manager, 'Đã đặt phòng.')).toBeVisible();
  expect((await bookingByTitle(request, await tokenFor(request, USERS.manager), reopened)).status).toBe('CONFIRMED');
});
