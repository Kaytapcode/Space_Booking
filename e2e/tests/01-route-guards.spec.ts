import { USERS } from '../support/api';
import { expect, test } from '../support/fixtures';
import { loginUi } from '../support/ui';

test('1a. unauthenticated visit to a protected route redirects to login', async ({ page }) => {
  for (const path of ['/my-bookings', '/approvals', '/admin/rooms', '/bookings/new']) {
    await page.goto(path);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Đăng nhập' })).toBeVisible();
  }
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' })).toHaveCount(0);
});

test('1b. an employee is blocked from /approvals and /admin', async ({ page }) => {
  await loginUi(page, USERS.employee);
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await expect(nav.getByRole('link', { name: 'Phê duyệt' })).toHaveCount(0);
  await expect(nav.getByRole('link', { name: 'Quản lý phòng' })).toHaveCount(0);

  for (const path of ['/approvals', '/admin/rooms', '/admin/audit']) {
    await page.goto(path);
    await expect(page).toHaveURL(/localhost:4200\/$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Lịch tổng quan' })).toBeVisible();
  }
});

test('1c. an admin cannot open the booking pages', async ({ page }) => {
  await loginUi(page, USERS.admin);
  for (const path of ['/bookings/new', '/my-bookings', '/approvals']) {
    await page.goto(path);
    await expect(page).toHaveURL(/localhost:4200\/$/);
  }
});
