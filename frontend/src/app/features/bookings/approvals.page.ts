import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import type { components } from '../../api/schema';
import { BookingsApi } from '../../core/api/bookings.service';
import { ToastService } from '../../core/ui/toast.service';
import { AsyncViewComponent } from '../../shared/async-view.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { PaginationComponent } from '../../shared/pagination.component';
import { BookingActionEvent, BookingListComponent } from './booking-list.component';

type BookingResponse = components['schemas']['BookingResponse'];

const PAGE_SIZE = 10;

@Component({
  selector: 'app-approvals-page',
  imports: [AsyncViewComponent, BookingListComponent, ConfirmDialogComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-4">
      <h1 class="text-h1 font-semibold">Phê duyệt</h1>

      <app-async-view
        [loading]="loading()"
        [error]="error()"
        [empty]="items().length === 0"
        emptyMessage="Không có yêu cầu nào chờ duyệt."
        (retry)="load()"
      >
        <app-booking-list mode="approvals" [items]="items()" [busyId]="busyId()" (act)="onAct($event)" />
      </app-async-view>

      <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="total()" (pageChange)="goTo($event)" />
    </div>

    @if (rejecting(); as b) {
      <app-confirm-dialog
        heading="Từ chối lịch đặt"
        [message]="'Từ chối lịch ' + b.title + ' của ' + b.user.fullName + '. Người đặt sẽ thấy lý do.'"
        confirmLabel="Từ chối"
        [danger]="true"
        [withReason]="true"
        [busy]="busyId() === b.id"
        (confirmed)="reject(b, $event)"
        (dismissed)="rejecting.set(null)"
      />
    }
  `,
})
export class ApprovalsPage {
  private readonly api = inject(BookingsApi);
  private readonly toast = inject(ToastService);

  protected readonly items = signal<BookingResponse[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly busyId = signal<string | null>(null);
  protected readonly rejecting = signal<BookingResponse | null>(null);
  private sub?: Subscription;

  constructor() {
    this.load();
    inject(DestroyRef).onDestroy(() => this.sub?.unsubscribe());
  }

  protected load(silent = false): void {
    if (!silent) {
      this.loading.set(true);
      this.error.set(false);
    }
    this.sub?.unsubscribe();
    this.sub = this.api.pending({ page: this.page(), size: PAGE_SIZE }).subscribe({
      next: (p) => {
        // A page emptied by the last action: step back instead of showing an empty page.
        if (p.content.length === 0 && p.page > 0) {
          this.goTo(p.page - 1);
          return;
        }
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

  protected goTo(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected onAct({ action, booking }: BookingActionEvent): void {
    if (action === 'approve') this.run(booking, this.api.approve(booking.id), 'Đã duyệt lịch đặt.');
    else if (action === 'reject') this.rejecting.set(booking);
  }

  protected reject(b: BookingResponse, reason: string): void {
    this.run(b, this.api.reject(b.id, reason), 'Đã từ chối lịch đặt.');
  }

  private run(b: BookingResponse, call: Observable<BookingResponse>, done: string): void {
    this.busyId.set(b.id);
    call.subscribe({
      next: () => {
        this.toast.success(done);
        this.busyId.set(null);
        this.rejecting.set(null);
        this.load(true);
      },
      error: () => {
        this.busyId.set(null);
        this.rejecting.set(null);
      },
    });
  }
}
