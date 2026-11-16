import { Locator, Page, expect } from '@playwright/test';
import { DAY } from './fixtures';
import { ROOMS, User } from './api';

type RoomKey = keyof typeof ROOMS;

export async function loginUi(page: Page, user: User): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(user.email);
  await page.getByLabel('Mật khẩu').fill(user.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Lịch tổng quan' })).toBeVisible();
}

export async function goTo(page: Page, navLabel: string, heading: string): Promise<void> {
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('link', { name: navLabel, exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
}

export const successToast = (page: Page, text: string): Locator => page.getByRole('status').filter({ hasText: text });
export const errorToast = (page: Page, text: string): Locator => page.getByRole('alert').filter({ hasText: text });

/** A booking as a table row (desktop) or a card (mobile). */
export const bookingItem = (page: Page, title: string): Locator =>
  page.getByRole('row').or(page.getByRole('listitem')).filter({ hasText: title });

export interface BookingInput {
  room: RoomKey;
  title: string;
  start: string;
  end: string;
  date?: string;
}

export async function openBookingForm(page: Page): Promise<void> {
  await goTo(page, 'Đặt phòng', 'Đặt phòng');
}

export async function fillBooking(page: Page, b: BookingInput): Promise<void> {
  const room = ROOMS[b.room];
  await page.getByLabel('Phòng', { exact: true }).selectOption({ label: `${room.name} · ${room.capacity} chỗ` });
  await page.getByLabel('Nội dung').fill(b.title);
  await page.getByLabel('Ngày', { exact: true }).fill(b.date ?? DAY);
  await page.getByLabel('Giờ bắt đầu').fill(b.start);
  await page.getByLabel('Giờ kết thúc').fill(b.end);
}

export async function submitBooking(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Đặt phòng', exact: true }).click();
}

/** Full happy path: nav to the form, fill it, submit, land on My bookings. */
export async function bookViaUi(page: Page, b: BookingInput): Promise<void> {
  await openBookingForm(page);
  await fillBooking(page, b);
  await submitBooking(page);
  await expect(page).toHaveURL(/\/my-bookings$/);
  await expect(bookingItem(page, b.title)).toBeVisible();
}
