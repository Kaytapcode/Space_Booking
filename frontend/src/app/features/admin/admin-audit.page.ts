import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import type { components } from '../../api/schema';
import { AuditApi, AuditQuery } from '../../core/api/audit.service';
import { AsyncViewComponent } from '../../shared/async-view.component';
import { FieldComponent } from '../../shared/field.component';
import { controlError, vnDisplayDate } from '../../shared/format';
import { PaginationComponent } from '../../shared/pagination.component';
import { BookingStatus, statusBadge } from '../../shared/status-badge';
import { vnTime } from '../../shared/vn-time';

type AuditAction = components['schemas']['AuditAction'];
type AuditLogResponse = components['schemas']['AuditLogResponse'];

const PAGE_SIZE = 20;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const ACTION_LABEL: Record<AuditAction, string> = {
  CREATED: 'Tạo mới',
  APPROVED: 'Duyệt',
  REJECTED: 'Từ chối',
  CANCELLED: 'Hủy',
  EXPIRED: 'Hết hạn',
  CHECKED_IN: 'Check-in',
  CHECKED_OUT: 'Checkout',
  COMPLETED: 'Hoàn thành',
  NO_SHOW: 'Vắng mặt',
};
const ACTIONS = Object.keys(ACTION_LABEL) as AuditAction[];

interface Details {
  from?: string | null;
  to?: string | null;
  reason?: string;
  endTime?: string;
}

@Component({
  selector: 'app-admin-audit-page',
  imports: [ReactiveFormsModule, AsyncViewComponent, FieldComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-4">
      <h1 class="text-h1 font-semibold">Nhật ký hệ thống</h1>

      <form [formGroup]="filters" (ngSubmit)="apply()" novalidate class="panel grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Lọc nhật ký">
        <app-field label="Mã lịch đặt" controlId="au-booking" [error]="bookingError()">
          <input id="au-booking" type="text" class="input" formControlName="bookingId" aria-describedby="au-booking-msg" [attr.aria-invalid]="bookingError() ? 'true' : null" />
        </app-field>
        <app-field label="Hành động" controlId="au-action">
          <select id="au-action" class="input" formControlName="action" aria-describedby="au-action-msg">
            <option value="">Tất cả</option>
            @for (a of actions; track a.value) {
              <option [value]="a.value">{{ a.label }}</option>
            }
          </select>
        </app-field>
        <app-field label="Từ ngày" controlId="au-from">
          <input id="au-from" type="date" class="input" formControlName="from" aria-describedby="au-from-msg" />
        </app-field>
        <app-field label="Đến ngày" controlId="au-to">
          <input id="au-to" type="date" class="input" formControlName="to" aria-describedby="au-to-msg" />
        </app-field>
        <div class="flex gap-2 sm:col-span-2 lg:col-span-4">
          <button type="submit" class="btn btn-primary">Lọc</button>
          <button type="button" class="btn btn-secondary" (click)="reset()">Xóa bộ lọc</button>
        </div>
      </form>

      <app-async-view
        [loading]="loading()"
        [error]="error()"
        [empty]="items().length === 0"
        emptyMessage="Không có bản ghi nào. Bỏ bớt bộ lọc."
        (retry)="load()"
      >
        <div class="panel overflow-x-auto">
          <table class="w-full">
            <caption class="sr-only">
              Nhật ký hệ thống
            </caption>
            <thead class="border-b border-n-200 bg-n-50">
              <tr>
                <th scope="col" class="th">Thời gian</th>
                <th scope="col" class="th">Hành động</th>
                <th scope="col" class="th">Người thực hiện</th>
                <th scope="col" class="th">Mã lịch đặt</th>
                <th scope="col" class="th">Chi tiết</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-n-200">
              @for (l of items(); track l.id) {
                <tr>
                  <td class="td whitespace-nowrap tabular-nums">{{ when(l) }}</td>
                  <td class="td">{{ actionLabel(l.action) }}</td>
                  <td class="td">{{ l.performedBy === 'SYSTEM_SCHEDULER' ? 'Hệ thống' : l.performedBy }}</td>
                  <td class="td tabular-nums" [title]="l.bookingId">{{ l.bookingId.slice(0, 8) }}</td>
                  <td class="td">{{ details(l) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </app-async-view>

      <app-pagination [page]="page()" [totalPages]="totalPages()" [totalElements]="total()" (pageChange)="goTo($event)" />
    </div>
  `,
})
export class AdminAuditPage {
  private readonly api = inject(AuditApi);

  protected readonly actions = ACTIONS.map((value) => ({ value, label: ACTION_LABEL[value] }));
  protected readonly filters = inject(NonNullableFormBuilder).group({
    bookingId: ['', Validators.pattern(UUID)],
    action: [''],
    from: [''],
    to: [''],
  });

  protected readonly items = signal<AuditLogResponse[]>([]);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  private applied: AuditQuery = {};
  private sub?: Subscription;

  constructor() {
    this.load();
    inject(DestroyRef).onDestroy(() => this.sub?.unsubscribe());
  }

  protected bookingError(): string | null {
    return controlError(this.filters.controls.bookingId, { pattern: 'Mã lịch đặt không hợp lệ.' });
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.sub?.unsubscribe();
    this.sub = this.api.search({ ...this.applied, page: this.page(), size: PAGE_SIZE }).subscribe({
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

  protected apply(): void {
    if (this.filters.invalid) {
      this.filters.markAllAsTouched();
      return;
    }
    const { bookingId, action, from, to } = this.filters.getRawValue();
    this.applied = {
      bookingId: bookingId.trim() || undefined,
      action: (action || undefined) as AuditAction | undefined,
      from: from || undefined,
      to: to || undefined,
    };
    this.goTo(0);
  }

  protected reset(): void {
    this.filters.reset();
    this.apply();
  }

  protected goTo(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected when(l: AuditLogResponse): string {
    return `${vnDisplayDate(l.createdAt)} ${vnTime(l.createdAt)}`;
  }

  protected actionLabel(a: AuditAction): string {
    return ACTION_LABEL[a];
  }

  /** `details` is JSON text `{from,to,reason?,endTime?}` (domain.md section 8). */
  protected details(l: AuditLogResponse): string {
    let d: Details;
    try {
      d = JSON.parse(l.details) as Details;
    } catch {
      return '';
    }
    const status = (s?: string | null) => (s ? (statusBadge(s as BookingStatus)?.label ?? s) : null);
    const parts: string[] = [];
    if (d.to) parts.push(`${status(d.from) ?? 'Mới'} → ${status(d.to)}`);
    if (d.reason) parts.push(`Lý do: ${d.reason}`);
    if (d.endTime) parts.push(`Kết thúc lúc ${vnTime(d.endTime)}`);
    return parts.join(' · ');
  }
}
