import { USERS, apiGet, apiSend, bookingByTitle, createBooking, tokenFor } from '../support/api';
import { desktopOnly, expect, test, wall } from '../support/fixtures';
import { errorToast, goTo, loginUi, successToast } from '../support/ui';

desktopOnly('the admin screens are a desktop table; mobile layout is covered by scenario 12');

interface RoomDto {
  id: string;
  name: string;
  capacity: number;
  active: boolean;
}

test('11. admin creates, edits, deactivates a room (blocked by future bookings) and finds audit rows', async ({
  page,
  request,
}) => {
  const name = 'Phòng Lan';
  const title = 'Họp giữ phòng Lan';
  await loginUi(page, USERS.admin);
  await goTo(page, 'Quản lý phòng', 'Quản lý phòng');
  const adminToken = await tokenFor(request, USERS.admin);
  const row = page.getByRole('row').filter({ hasText: name });

  // Create
  await page.getByRole('button', { name: 'Thêm phòng', exact: true }).click();
  let dialog = page.getByRole('dialog', { name: 'Thêm phòng', exact: true });
  await dialog.getByLabel('Tên phòng').fill(name);
  await dialog.getByLabel('Vị trí').fill('Tầng 4');
  await dialog.getByLabel('Sức chứa').fill('8');
  await dialog.getByLabel('Thiết bị').fill('tv, projector');
  await dialog.getByRole('button', { name: 'Lưu' }).click();
  await expect(successToast(page, 'Đã thêm phòng.')).toBeVisible();
  await expect(row).toContainText('Tầng 4');
  await expect(row).toContainText('projector, tv');
  await expect(row).toContainText('Hoạt động');

  // Edit
  await page.getByRole('button', { name: `Sửa phòng ${name}` }).click();
  dialog = page.getByRole('dialog', { name: 'Sửa phòng', exact: true });
  await dialog.getByLabel('Sức chứa').fill('10');
  await dialog.getByRole('button', { name: 'Lưu' }).click();
  await expect(successToast(page, 'Đã lưu phòng.')).toBeVisible();
  await expect(row.getByRole('cell', { name: '10', exact: true })).toBeVisible();
  const rooms = await apiGet<RoomDto[]>(request, adminToken, '/rooms');
  expect(rooms.find((r) => r.name === name)?.capacity).toBe(10);

  // Arranged data: an employee holds a future booking in the new room.
  const employeeToken = await tokenFor(request, USERS.employee);
  const bookingId = await createBooking(request, employeeToken, {
    roomName: name,
    title,
    start: wall('10:00'),
    end: wall('11:00'),
  });

  // Deactivation is refused while the booking is in the future.
  const deactivate = async () => {
    await page.getByRole('button', { name: `Ngừng hoạt động phòng ${name}` }).click();
    const confirm = page.getByRole('dialog', { name: 'Ngừng hoạt động phòng', exact: true });
    await confirm.getByRole('button', { name: 'Ngừng hoạt động', exact: true }).click();
  };
  await deactivate();
  await expect(errorToast(page, 'Phòng còn lịch đặt sắp tới. Hủy các lịch đó trước.')).toBeVisible();
  await expect(page.getByRole('button', { name: `Ngừng hoạt động phòng ${name}` })).toBeVisible();
  expect((await apiGet<RoomDto[]>(request, adminToken, '/rooms')).find((r) => r.name === name)?.active).toBe(true);

  // After the booking is cancelled the room can be deactivated.
  const cancelled = await apiSend(request, employeeToken, 'patch', `/bookings/${bookingId}/cancel`);
  expect(cancelled.status()).toBe(200);
  await deactivate();
  await expect(successToast(page, 'Đã ngừng hoạt động phòng.')).toBeVisible();
  await expect(row).toContainText('Ngừng hoạt động');
  await expect(page.getByRole('button', { name: `Ngừng hoạt động phòng ${name}` })).toHaveCount(0);
  const all = await apiGet<RoomDto[]>(request, adminToken, '/rooms?includeInactive=true');
  expect(all.find((r) => r.name === name)?.active).toBe(false);
  expect((await bookingByTitle(request, employeeToken, title)).status).toBe('CANCELLED');

  // Audit: the booking made in this room has CREATED and CANCELLED rows.
  await goTo(page, 'Nhật ký', 'Nhật ký hệ thống');
  await page.getByLabel('Mã lịch đặt').fill(bookingId);
  await page.getByRole('button', { name: 'Lọc', exact: true }).click();
  const created = page.getByRole('row').filter({ hasText: 'Tạo mới' });
  const cancel = page.getByRole('row').filter({ hasText: 'Hủy' });
  await expect(created).toHaveCount(1);
  await expect(created).toContainText(USERS.employee.email);
  await expect(created).toContainText(bookingId.slice(0, 8));
  await expect(cancel).toHaveCount(1);
  await expect(cancel).toContainText(USERS.employee.email);

  const logs = await apiGet<{ content: { action: string }[] }>(request, adminToken, `/audit-logs?bookingId=${bookingId}`);
  expect(logs.content.map((l) => l.action).sort()).toEqual(['CANCELLED', 'CREATED']);
});
