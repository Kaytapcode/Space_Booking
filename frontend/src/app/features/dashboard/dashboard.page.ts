import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import type { components } from '../../api/schema';
import { AuthService } from '../../core/auth/auth.service';
import { BookingsApi } from '../../core/api/bookings.service';
import { AsyncViewComponent } from '../../shared/async-view.component';
import { DialogComponent } from '../../shared/dialog.component';
import { displayDate, timeRange } from '../../shared/format';
import { IconComponent } from '../../shared/icon.component';
import { desktopSignal } from '../../shared/media';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { StatusBadge, statusBadge } from '../../shared/status-badge';
import { addDays, vnToday, vnTime } from '../../shared/vn-time';
import {
  COLS,
  DAY_END,
  DAY_START,
  RoomRow,
  SLOT,
  ScheduleBooking,
  buildRow,
  hhmm,
  toMinutes,
} from './timeline-model';

type DailySchedule = components['schemas']['DailyScheduleResponse'];

const WEEKDAY = new Intl.DateTimeFormat('vi-VN', { weekday: 'short', timeZone: 'Asia/Ho_Chi_Minh' });
const NAV_KEYS = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];

@Component({
  selector: 'app-dashboard-page',
  imports: [AsyncViewComponent, DialogComponent, IconComponent, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-4">
      <h1 class="text-h1 font-semibold">Lịch tổng quan</h1>

      <div class="flex flex-wrap items-end gap-2" role="group" aria-label="Chọn ngày">
        <button type="button" class="btn btn-secondary" aria-label="Ngày trước" (click)="shift(-1)">
          <app-icon name="chevron-left" />
        </button>
        <button type="button" class="btn btn-secondary" (click)="setDate(today())">Hôm nay</button>
        <button type="button" class="btn btn-secondary" aria-label="Ngày sau" (click)="shift(1)">
          <app-icon name="chevron-right" />
        </button>
        <div class="flex flex-col gap-1">
          <label for="dash-date" class="font-medium">Ngày</label>
          <input id="dash-date" type="date" class="input" [value]="date()" (change)="onDate($event)" />
        </div>
        <p class="pb-2 font-medium tabular-nums" aria-live="polite">{{ dateLabel() }}</p>
      </div>

      @if (!desktop()) {
        <nav aria-label="Chọn nhanh ngày" class="grid grid-cols-7 gap-1">
          @for (d of days(); track d.date) {
            <button
              type="button"
              class="btn flex-col gap-0 px-0"
              [class]="d.date === date() ? 'btn-primary' : 'btn-secondary'"
              [attr.aria-current]="d.date === date() ? 'date' : null"
              [attr.aria-label]="d.label"
              (click)="setDate(d.date)"
            >
              <span>{{ d.weekday }}&#32;</span>
              <span class="tabular-nums">{{ d.day }}</span>
            </button>
          }
        </nav>
      }

      <form class="panel flex flex-wrap items-end gap-4 p-4" (submit)="$event.preventDefault()" aria-label="Lọc phòng">
        <div class="flex flex-col gap-1">
          <label for="dash-cap" class="font-medium">Sức chứa tối thiểu</label>
          <input
            id="dash-cap"
            type="number"
            min="1"
            inputmode="numeric"
            class="input w-40"
            [value]="minCapacity() ?? ''"
            (input)="onCapacity($event)"
          />
        </div>
        @if (equipmentOptions().length) {
          <fieldset class="flex flex-col gap-1">
            <legend class="font-medium">Thiết bị</legend>
            <div class="flex flex-wrap gap-x-4">
              @for (e of equipmentOptions(); track e) {
                <label class="flex min-h-11 items-center gap-2 lg:min-h-9">
                  <input
                    type="checkbox"
                    class="size-5 accent-primary-700 lg:size-4"
                    [checked]="equipment().includes(e)"
                    (change)="toggleEquipment(e)"
                  />
                  {{ e }}
                </label>
              }
            </div>
          </fieldset>
        }
        @if (filtersActive()) {
          <button type="button" class="btn btn-ghost" (click)="clearFilters()">Xóa bộ lọc</button>
        }
      </form>

      <app-async-view
        [loading]="loading()"
        [error]="error()"
        [empty]="rows().length === 0"
        emptyMessage="Không có phòng phù hợp. Bỏ bớt bộ lọc."
        [skeleton]="desktop() ? 'row' : 'card'"
        (retry)="load()"
      >
        @if (desktop()) {
          <div
            class="panel max-h-[70vh] overflow-auto"
            role="group"
            aria-label="Lịch phòng theo giờ"
            (keydown)="onKey($event)"
            (focusin)="onFocusIn($event)"
          >
            <div
              class="relative grid min-w-[1200px]"
              [style.grid-template-columns]="'160px repeat(' + cols + ', minmax(40px, 1fr))'"
              [style.grid-template-rows]="'40px repeat(' + rows().length + ', 48px)'"
            >
              <div class="sticky left-0 top-0 z-30 border-b border-r border-n-200 bg-n-0 px-3 py-2 font-medium" style="grid-area: 1 / 1">
                Phòng
              </div>
              @for (h of hours; track h.col) {
                <div
                  class="sticky top-0 z-20 border-b border-l border-n-200 bg-n-0 px-1 pt-2 text-caption tabular-nums text-n-600"
                  [style.grid-area]="'1 / ' + (h.col + 2) + ' / 2 / ' + (h.col + 4)"
                >
                  {{ h.label }}
                </div>
              }
              @for (r of rows(); track r.schedule.room.id; let ri = $index) {
                <div
                  class="sticky left-0 z-20 flex min-w-0 flex-col justify-center border-b border-r border-n-200 bg-n-0 px-3"
                  [style.grid-area]="ri + 2 + ' / 1 / ' + (ri + 3) + ' / 2'"
                  [title]="roomMeta(r)"
                >
                  <span class="truncate font-medium">{{ r.schedule.room.name }}</span>
                  <span class="truncate text-n-600">{{ roomMeta(r) }}</span>
                </div>
                @for (c of r.cells; track c.col) {
                  @if (c.kind === 'free') {
                    <button
                      type="button"
                      class="border-b border-l border-n-200 hover:bg-primary-50 focus-visible:z-30 disabled:bg-n-100 disabled:hover:bg-n-100"
                      [style.grid-area]="ri + 2 + ' / ' + (c.col + 2) + ' / ' + (ri + 3) + ' / ' + (c.col + 3)"
                      [attr.data-row]="ri"
                      [attr.data-col]="c.col"
                      [attr.tabindex]="tabKey() === ri + ':' + c.col ? 0 : -1"
                      [disabled]="c.past || !canBook()"
                      [attr.aria-label]="'Đặt ' + r.schedule.room.name + ' lúc ' + c.time"
                      (click)="book(r.schedule.room.id, c.time)"
                    ></button>
                  } @else {
                    <button
                      type="button"
                      class="m-0.5 flex min-w-0 items-center overflow-hidden rounded-sm focus-visible:z-30 border-l-[3px] border-(--dot) px-2 text-left"
                      [class]="badge(c.booking).classes"
                      [style.grid-area]="ri + 2 + ' / ' + (c.col + 2) + ' / ' + (ri + 3) + ' / ' + (c.col + 2 + c.span)"
                      [attr.data-row]="ri"
                      [attr.data-col]="c.col"
                      [attr.tabindex]="tabKey() === ri + ':' + c.col ? 0 : -1"
                      [title]="bookingName(r, c.booking)"
                      [attr.aria-label]="bookingName(r, c.booking)"
                      (click)="detail.set({ room: r.schedule.room.name, booking: c.booking })"
                    >
                      <app-icon class="mr-1 shrink-0" [name]="badge(c.booking).icon" />
                      <span class="truncate tabular-nums">{{ range(c.booking) }} {{ c.booking.user.fullName }}</span>
                    </button>
                  }
                }
              }
              @if (nowLeft(); as left) {
                <div class="pointer-events-none absolute inset-y-0 z-10 w-0.5 bg-primary-700" aria-hidden="true" [style.left]="left"></div>
              }
            </div>
          </div>
        } @else {
          <ul class="grid gap-3 md:grid-cols-2">
            @for (r of rows(); track r.schedule.room.id) {
              <li class="panel flex flex-col gap-3 p-4">
                <div>
                  <h3 class="text-h3 font-semibold">{{ r.schedule.room.name }}</h3>
                  <p class="text-n-600">{{ roomMeta(r) }}</p>
                </div>
                <div role="group" [attr.aria-label]="'Khung giờ trống của ' + r.schedule.room.name" class="flex flex-wrap gap-2">
                  @for (t of r.freeTimes.slice(0, 4); track t) {
                    @if (canBook()) {
                      <button
                        type="button"
                        class="btn btn-secondary tabular-nums"
                        [attr.aria-label]="'Đặt ' + r.schedule.room.name + ' lúc ' + t"
                        (click)="book(r.schedule.room.id, t)"
                      >
                        {{ t }}
                      </button>
                    } @else {
                      <span class="rounded-sm bg-n-100 px-2 py-1 tabular-nums text-n-600">{{ t }}</span>
                    }
                  } @empty {
                    <p class="text-n-600">Hết khung giờ trống trong ngày.</p>
                  }
                </div>
                @if (canBook()) {
                  <button type="button" class="btn btn-primary w-full" (click)="book(r.schedule.room.id, r.freeTimes[0])">
                    Đặt nhanh
                  </button>
                }
                @if (r.bookings.length) {
                  <ul class="flex flex-col divide-y divide-n-200 border-t border-n-200">
                    @for (b of r.bookings; track b.id) {
                      <li class="flex flex-wrap items-center justify-between gap-2 py-2">
                        <span class="tabular-nums">{{ range(b) }}</span>
                        <span class="min-w-0 flex-1 truncate">{{ b.user.fullName }}</span>
                        <app-status-badge [status]="b.status" />
                      </li>
                    }
                  </ul>
                }
              </li>
            }
          </ul>
        }
      </app-async-view>
    </div>

    @if (detail(); as d) {
      <app-dialog heading="Chi tiết lịch đặt" (dismissed)="detail.set(null)">
        <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
          <dt class="text-n-600">Phòng</dt>
          <dd>{{ d.room }}</dd>
          <dt class="text-n-600">Nội dung</dt>
          <dd>{{ d.booking.title }}</dd>
          <dt class="text-n-600">Người đặt</dt>
          <dd>{{ d.booking.user.fullName }}</dd>
          <dt class="text-n-600">Giờ</dt>
          <dd class="tabular-nums">{{ range(d.booking) }}</dd>
          <dt class="text-n-600">Trạng thái</dt>
          <dd><app-status-badge [status]="d.booking.status" /></dd>
        </dl>
        <div class="flex justify-end">
          <button type="button" class="btn btn-secondary" (click)="detail.set(null)">Đóng</button>
        </div>
      </app-dialog>
    }
  `,
})
export class DashboardPage {
  private readonly api = inject(BookingsApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly cols = COLS;
  protected readonly hours = Array.from({ length: COLS / 2 }, (_, h) => ({
    col: h * 2,
    label: hhmm(DAY_START + h * 60),
  }));

  protected readonly desktop = desktopSignal();
  protected readonly today = signal(vnToday());
  private readonly nowMin = signal(toMinutes(vnTime(new Date())));

  protected readonly date = signal(vnToday());
  private readonly schedule = signal<DailySchedule | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);

  protected readonly minCapacity = signal<number | null>(null);
  protected readonly equipment = signal<string[]>([]);
  protected readonly detail = signal<{ room: string; booking: ScheduleBooking } | null>(null);
  private readonly activeKey = signal<string | null>(null);

  protected readonly canBook = computed(() => {
    const role = this.auth.role();
    return role === 'EMPLOYEE' || role === 'MANAGER';
  });
  protected readonly filtersActive = computed(() => this.minCapacity() !== null || this.equipment().length > 0);

  protected readonly dateLabel = computed(() => displayDate(this.date()));
  protected readonly days = computed(() =>
    [-3, -2, -1, 0, 1, 2, 3].map((n) => {
      const d = addDays(this.date(), n);
      const weekday = WEEKDAY.format(new Date(`${d}T00:00:00+07:00`));
      return { date: d, day: d.slice(8), weekday, label: `${weekday} ${d.slice(8)}, ${displayDate(d)}` };
    }),
  );

  protected readonly equipmentOptions = computed(() => {
    const all = this.schedule()?.rooms.flatMap((r) => r.room.equipments) ?? [];
    return [...new Set(all)].sort();
  });

  protected readonly rows = computed<RoomRow[]>(() => {
    const data = this.schedule();
    if (!data) return [];
    const date = this.date();
    const today = this.today();
    const now = date < today ? Infinity : date > today ? -Infinity : this.nowMin();
    const cap = this.minCapacity();
    const sel = this.equipment();
    return data.rooms
      .filter((r) => (cap === null || r.room.capacity >= cap) && sel.every((e) => r.room.equipments.includes(e)))
      .map((r) => buildRow(r, date, now));
  });

  protected readonly nowLeft = computed(() => {
    const now = this.nowMin();
    if (this.date() !== this.today() || now < DAY_START || now > DAY_END) return null;
    return `calc(160px + (100% - 160px) * ${(now - DAY_START) / (DAY_END - DAY_START)})`;
  });

  private readonly enabledKeys = computed(() =>
    this.rows().flatMap((r, ri) =>
      r.cells.flatMap((c) => (c.kind === 'booking' || (!c.past && this.canBook()) ? [`${ri}:${c.col}`] : [])),
    ),
  );
  /** Roving tabindex: one tab stop for the whole grid. */
  protected readonly tabKey = computed(() => {
    const keys = this.enabledKeys();
    const active = this.activeKey();
    return active && keys.includes(active) ? active : (keys[0] ?? null);
  });

  private sub?: Subscription;

  constructor() {
    this.load();
    const timer = setInterval(() => {
      this.nowMin.set(toMinutes(vnTime(new Date())));
      this.today.set(vnToday());
    }, 30_000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(timer);
      this.sub?.unsubscribe();
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.sub?.unsubscribe();
    this.sub = this.api.dailySchedule(this.date()).subscribe({
      next: (s) => {
        this.schedule.set(s);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected setDate(date: string): void {
    if (date === this.date()) return;
    this.date.set(date);
    this.activeKey.set(null);
    this.load();
  }

  protected shift(days: number): void {
    this.setDate(addDays(this.date(), days));
  }

  protected onDate(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (value) this.setDate(value);
  }

  protected onCapacity(event: Event): void {
    const n = Number((event.target as HTMLInputElement).value);
    this.minCapacity.set(Number.isFinite(n) && n >= 1 ? Math.floor(n) : null);
  }

  protected toggleEquipment(token: string): void {
    this.equipment.update((all) => (all.includes(token) ? all.filter((e) => e !== token) : [...all, token]));
  }

  protected clearFilters(): void {
    this.minCapacity.set(null);
    this.equipment.set([]);
  }

  protected badge(b: ScheduleBooking): StatusBadge {
    return statusBadge(b.status);
  }

  protected range(b: ScheduleBooking): string {
    return timeRange(b.startTime, b.endTime);
  }

  protected roomMeta(r: RoomRow): string {
    const { capacity, equipments, location } = r.schedule.room;
    return [`${capacity} chỗ`, location, equipments.join(', ')].filter(Boolean).join(' · ');
  }

  protected bookingName(r: RoomRow, b: ScheduleBooking): string {
    // Starts with the visible text (WCAG 2.5.3 label in name).
    return `${this.range(b)} ${b.user.fullName}, ${r.schedule.room.name}, ${b.title}, ${statusBadge(b.status).label}`;
  }

  protected book(roomId: string, time?: string): void {
    const queryParams: Record<string, string> = { roomId, date: this.date() };
    if (time) {
      queryParams['start'] = time;
      queryParams['end'] = hhmm(Math.min(toMinutes(time) + SLOT, DAY_END));
    }
    void this.router.navigate(['/bookings/new'], { queryParams });
  }

  protected onFocusIn(event: FocusEvent): void {
    const btn = (event.target as HTMLElement).closest<HTMLElement>('button[data-row]');
    if (btn) this.activeKey.set(`${btn.dataset['row']}:${btn.dataset['col']}`);
  }

  /** Arrow keys move between enabled cells; left/right inside a row, up/down to the nearest column in the next row. */
  protected onKey(event: KeyboardEvent): void {
    if (!NAV_KEYS.includes(event.key)) return;
    const current = (event.target as HTMLElement).closest<HTMLButtonElement>('button[data-row]');
    if (!current) return;
    const root = event.currentTarget as HTMLElement;
    const row = Number(current.dataset['row']);
    const col = Number(current.dataset['col']);
    const inRow = (r: number) =>
      Array.from(root.querySelectorAll<HTMLButtonElement>(`button[data-row="${r}"]:not(:disabled)`));

    let target: HTMLButtonElement | undefined;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      const list = inRow(row);
      target = list[list.indexOf(current) + (event.key === 'ArrowRight' ? 1 : -1)];
    } else {
      const step = event.key === 'ArrowDown' ? 1 : -1;
      for (let r = row + step; r >= 0 && r < this.rows().length && !target; r += step) {
        target = inRow(r).reduce<HTMLButtonElement | undefined>(
          (best, b) =>
            !best || Math.abs(Number(b.dataset['col']) - col) < Math.abs(Number(best.dataset['col']) - col) ? b : best,
          undefined,
        );
      }
    }
    if (target) {
      event.preventDefault();
      target.focus();
    }
  }
}
