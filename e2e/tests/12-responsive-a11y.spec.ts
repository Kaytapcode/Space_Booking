import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';
import { USERS, createBooking, tokenFor } from '../support/api';
import { expect, test, wall } from '../support/fixtures';
import { goTo, loginUi } from '../support/ui';

async function seriousViolations(page: Page): Promise<string[]> {
  const { violations } = await new AxeBuilder({ page }).analyze();
  return violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.impact} ${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`);
}

function horizontalOverflow(page: Page): Promise<number> {
  return page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
}

test('12a. desktop 1280: the timeline grid is shown, not the cards', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'timeline grid is the >= 1024px layout');
  await loginUi(page, USERS.employee);
  await expect(page.getByRole('group', { name: 'Lịch phòng theo giờ' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Đặt Phòng Sen lúc 09:00', exact: true })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Khung giờ trống của Phòng Sen' })).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: 'Chọn nhanh ngày' })).toHaveCount(0);
});

test('12b. mobile 390: cards, no horizontal scroll, one tap starts a booking', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile', 'card layout and touch are the < 1024px layout');
  await loginUi(page, USERS.employee);
  await expect(page.getByRole('group', { name: 'Khung giờ trống của Phòng Sen' })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Lịch phòng theo giờ' })).toHaveCount(0);

  expect(await horizontalOverflow(page),'horizontal overflow in px').toBeLessThanOrEqual(0);

  await page.getByRole('button', { name: 'Đặt Phòng Sen lúc 08:30', exact: true }).tap();
  await expect(page.getByRole('heading', { level: 1, name: 'Đặt phòng' })).toBeVisible();
  await expect(page.getByLabel('Giờ bắt đầu')).toHaveValue('08:30');
  await expect(page.getByLabel('Giờ kết thúc')).toHaveValue('09:00');
  await expect(page.getByRole('option', { name: 'Phòng Sen · 6 chỗ', selected: true })).toBeAttached();
  expect(await horizontalOverflow(page),'horizontal overflow on the booking form in px').toBeLessThanOrEqual(0);
});

test('12c. axe: no serious or critical violations on Dashboard, Booking form, My bookings', async ({ page, request }) => {
  // Arranged data so My bookings renders a populated list.
  const token = await tokenFor(request, USERS.employee);
  await createBooking(request, token, {
    roomName: 'Phòng Sen',
    title: 'Họp kiểm tra truy cập',
    start: wall('09:00'),
    end: wall('10:00'),
  });

  await loginUi(page, USERS.employee);
  await expect(page.getByText('Phòng Sen').first()).toBeVisible();
  const found: string[] = [];
  const scan = async (label: string) => found.push(...(await seriousViolations(page)).map((v) => `${label} -> ${v}`));
  await scan('Dashboard');

  await goTo(page, 'Đặt phòng', 'Đặt phòng');
  await expect(page.getByRole('option', { name: 'Phòng Sen · 6 chỗ' })).toBeAttached();
  await scan('Booking form');

  await goTo(page, 'Lịch của tôi', 'Lịch của tôi');
  await expect(page.getByText('Họp kiểm tra truy cập')).toBeVisible();
  await scan('My bookings');

  expect(found).toEqual([]);
});
