import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import type { components } from '../../api/schema';
import { BookingsApi } from '../../core/api/bookings.service';
import { ToastService } from '../../core/ui/toast.service';
import { AsyncViewComponent } from '../../shared/async-view.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { PaginationComponent } from '../../shared/pagination.component';
import { BookingStatus, statusBadge } from '../../shared/status-badge';
import { BookingActionEvent, BookingListComponent } from './booking-list.component';

type BookingResponse = components['schemas']['BookingResponse'];

const PAGE_SIZE = 10;
const STATUSES: BookingStatus[] = ['PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW'];

@Component({
  selector: 'app-my-bookings-page',
  imports: [RouterLink, AsyncViewComponent, BookingListComponent, ConfirmDialogComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-4">
      <div class="flex flex-wrap items-end justify-between gap-2">
        <h1 class="text-h1 font-semibold">Lịch của tôi</h1>
        <div class="flex flex-col gap-1">
          <label for="mb-status" class="font-medium">Trạng thái</label>
          <select id="mb-status" class="input" (change)="onStatus($event)">
            <option value="">Tất cả</option>
            @for (s of statuses; track s.value) {
              <option [value]="s.value">{{ s.label }}</option>
            }
          </select>
        </div>
      </div>

      <app-async-view
        [loading]="loading()"
        [error]="error()"
        [empty]="items().length === 0"
        emptyMessage="Chưa có lịch đặt nào. Chọn phòng và khung giờ để đặt."
        (retry)="load()"
      >
        <a empty-action routerLink="/bookings/new" class="btn btn-primary">Đặt phòng</a>
        <app-booking-list [items]="items()" [busyId]="busyId()" (act)="onAct($event)" />
      </app-async-view>

      <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="total()" (pageChange)="goTo($event)" />
    </div>

    @if (cancelling(); as b) {
      <app-confirm-dialog
        heading="Hủy lịch đặt"
        [message]="'Hủy lịch ' + b.title + ' tại ' + b.room.name + '. Thao tác này không hoàn tác được.'"
        confirmLabel="Hủy lịch"
        [danger]="true"
        [busy]="busyId() === b.id"
        (confirmed)="cancel(b)"
        (dismissed)="cancelling.set(null)"
      />
    }
  `,
})
export class MyBookingsPage {
  private readonly api = inject(BookingsApi);
  private readonly toast = inject(ToastService);

  protected readonly statuses = STATUSES.map((value) => ({ value, label: statusBadge(value).label }));
  protected readonly items = signal<BookingResponse[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly busyId = signal<string | null>(null);
  protected readonly cancelling = signal<BookingResponse | null>(null);
  private status: BookingStatus | undefined;
  private sub?: Subscription;

  constructor() {
    this.load();
    inject(DestroyRef).onDestroy(() => this.sub?.unsubscribe());
  }

  /** `silent` keeps the current rows on screen while refreshing after an action. */
  protected load(silent = false): void {
    if (!silent) {
      this.loading.set(true);
      this.error.set(false);
    }
    this.sub?.unsubscribe();
    this.sub = this.api.myBookings({ page: this.page(), size: PAGE_SIZE, status: this.status }).subscribe({
      next: (p) => {
        this.items.set(p.content);
        this.totalPages.set(p.totalPages);
        this.total.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected onStatus(event: Event): void {
    this.status = ((event.target as HTMLSelectElement).value || undefined) as BookingStatus | undefined;
    this.goTo(0);
  }

  protected goTo(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected onAct({ action, booking }: BookingActionEvent): void {
    if (action === 'checkIn') this.run(booking, this.api.checkIn(booking.id), 'Đã check-in.');
    else if (action === 'checkOut') this.run(booking, this.api.checkOut(booking.id), 'Đã checkout. Phòng được trả sớm.');
    else if (action === 'cancel') this.cancelling.set(booking);
  }

  protected cancel(b: BookingResponse): void {
    this.run(b, this.api.cancel(b.id), 'Đã hủy lịch đặt.');
  }

  private run(b: BookingResponse, call: Observable<BookingResponse>, done: string): void {
    this.busyId.set(b.id);
    call.subscribe({
      next: () => {
        this.toast.success(done);
        this.busyId.set(null);
        this.cancelling.set(null);
        this.load(true);
      },
      error: () => {
        this.busyId.set(null);
        this.cancelling.set(null);
      },
    });
  }
}
