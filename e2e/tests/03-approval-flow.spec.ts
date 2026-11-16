import { USERS, bookingByTitle, tokenFor } from '../support/api';
import { desktopOnly, expect, test } from '../support/fixtures';
import { bookViaUi, bookingItem, goTo, loginUi, successToast } from '../support/ui';

desktopOnly('workflow is viewport-independent; mobile layout is covered by scenario 12');

test('3. requires_approval booking is PENDING, manager approves, employee sees CONFIRMED', async ({ page, request, world }) => {
  const title = 'Họp ban điều hành';
  await loginUi(page, USERS.employee);
  await bookViaUi(page, { room: 'truongSon', title, start: '10:00', end: '11:00' });
  await expect(successToast(page, 'Đã gửi yêu cầu. Chờ quản lý duyệt.')).toBeVisible();
  await expect(bookingItem(page, title)).toContainText('Chờ duyệt');
  const employeeToken = await tokenFor(request, USERS.employee);
  expect((await bookingByTitle(request, employeeToken, title)).status).toBe('PENDING');

  const manager = await world.newPage();
  await loginUi(manager, USERS.manager);
  await goTo(manager, 'Phê duyệt', 'Phê duyệt');
  const pending = bookingItem(manager, title);
  await expect(pending).toContainText(USERS.employee.fullName);
  await manager.getByRole('button', { name: `Duyệt lịch ${title}` }).click();
  await expect(successToast(manager, 'Đã duyệt lịch đặt.')).toBeVisible();
  await expect(pending).toHaveCount(0);
  await expect(manager.getByText('Không có yêu cầu nào chờ duyệt.')).toBeVisible();

  await page.reload();
  await expect(bookingItem(page, title)).toContainText('Đã xác nhận');
  expect((await bookingByTitle(request, employeeToken, title)).status).toBe('CONFIRMED');
});

test('4. manager rejects with a reason; employee sees CANCELLED and the reason', async ({ page, request, world }) => {
  const title = 'Họp đối tác';
  const reason = 'Phòng dành cho sự kiện nội bộ';
  await loginUi(page, USERS.employee);
  await bookViaUi(page, { room: 'truongSon', title, start: '13:00', end: '14:00' });

  const manager = await world.newPage();
  await loginUi(manager, USERS.manager);
  await goTo(manager, 'Phê duyệt', 'Phê duyệt');
  await manager.getByRole('button', { name: `Từ chối lịch ${title}` }).click();

  const dialog = manager.getByRole('dialog', { name: 'Từ chối lịch đặt' });
  await expect(dialog).toBeVisible();
  // A reason is mandatory: confirming an empty form stays in the dialog.
  await dialog.getByRole('button', { name: 'Từ chối', exact: true }).click();
  await expect(dialog.getByText('Nhập lý do.')).toBeVisible();
  await dialog.getByLabel('Lý do').fill(reason);
  await dialog.getByRole('button', { name: 'Từ chối', exact: true }).click();
  await expect(successToast(manager, 'Đã từ chối lịch đặt.')).toBeVisible();
  await expect(bookingItem(manager, title)).toHaveCount(0);

  await page.reload();
  const item = bookingItem(page, title);
  await expect(item).toContainText('Đã hủy');
  await expect(item).toContainText(`Lý do: ${reason}`);

  const booking = await bookingByTitle(request, await tokenFor(request, USERS.employee), title);
  expect(booking.status).toBe('CANCELLED');
  expect(booking.rejectionReason).toBe(reason);
});
