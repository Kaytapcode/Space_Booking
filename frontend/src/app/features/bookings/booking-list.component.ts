import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { components } from '../../api/schema';
import { timeRange, vnDisplayDate } from '../../shared/format';
import { desktopSignal } from '../../shared/media';
import { StatusBadgeComponent } from '../../shared/status-badge.component';

type BookingResponse = components['schemas']['BookingResponse'];

export type BookingAction = 'checkIn' | 'checkOut' | 'cancel' | 'approve' | 'reject';
export interface BookingActionEvent {
  action: BookingAction;
  booking: BookingResponse;
}

interface ActionDef {
  action: BookingAction;
  label: string;
  style: string;
}

const CHECK_IN: ActionDef = { action: 'checkIn', label: 'Check-in', style: 'btn-primary' };
const CHECK_OUT: ActionDef = { action: 'checkOut', label: 'Checkout', style: 'btn-primary' };
const CANCEL: ActionDef = { action: 'cancel', label: 'Hủy', style: 'btn-secondary' };
const APPROVE: ActionDef = { action: 'approve', label: 'Duyệt', style: 'btn-primary' };
const REJECT: ActionDef = { action: 'reject', label: 'Từ chối', style: 'btn-secondary' };

/**
 * Bookings as a table (desktop) or cards (mobile). Actions follow the state machine in domain.md section 4;
 * time windows are enforced by the server and surface as a toast, so they are not gated here.
 */
@Component({
  selector: 'app-booking-list',
  imports: [NgTemplateOutlet, StatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-template #actionsTpl let-b>
      <div class="flex flex-wrap gap-2">
        @for (a of actionsFor(b); track a.action) {
          <button
            type="button"
            class="btn"
            [class]="a.style"
            [disabled]="busyId() === b.id"
            [attr.aria-label]="a.label + ' lịch ' + b.title"
            (click)="act.emit({ action: a.action, booking: b })"
          >
            {{ a.label }}
          </button>
        }
      </div>
    </ng-template>
    <ng-template #statusTpl let-b>
      <app-status-badge [status]="b.status" />
      @if (b.rejectionReason) {
        <p class="mt-1 text-n-600">Lý do: {{ b.rejectionReason }}</p>
      }
    </ng-template>

    @if (desktop()) {
      <div class="panel overflow-x-auto">
        <table class="w-full">
          <caption class="sr-only">
            {{ caption() }}
          </caption>
          <thead class="border-b border-n-200 bg-n-50">
            <tr>
              <th scope="col" class="th">Phòng</th>
              <th scope="col" class="th">Nội dung</th>
              @if (mode() === 'approvals') {
                <th scope="col" class="th">Người đặt</th>
              }
              <th scope="col" class="th">Ngày</th>
              <th scope="col" class="th">Giờ</th>
              <th scope="col" class="th">Trạng thái</th>
              <th scope="col" class="th">Thao tác</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-n-200">
            @for (b of items(); track b.id) {
              <tr class="min-h-10">
                <td class="td font-medium">{{ b.room.name }}</td>
                <td class="td">{{ b.title }}</td>
                @if (mode() === 'approvals') {
                  <td class="td">{{ b.user.fullName }}</td>
                }
                <td class="td tabular-nums">{{ date(b) }}</td>
                <td class="td tabular-nums">{{ range(b) }}</td>
                <td class="td"><ng-container [ngTemplateOutlet]="statusTpl" [ngTemplateOutletContext]="{ $implicit: b }" /></td>
                <td class="td"><ng-container [ngTemplateOutlet]="actionsTpl" [ngTemplateOutletContext]="{ $implicit: b }" /></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <ul class="grid gap-3 md:grid-cols-2">
        @for (b of items(); track b.id) {
          <li class="panel flex flex-col gap-2 p-4">
            <div class="flex items-start justify-between gap-2">
              <h3 class="text-h3 font-semibold">{{ b.room.name }}</h3>
              <span class="tabular-nums text-n-600">{{ date(b) }} · {{ range(b) }}</span>
            </div>
            <p>{{ b.title }}</p>
            @if (mode() === 'approvals') {
              <p class="text-n-600">Người đặt: {{ b.user.fullName }}</p>
            }
            <div><ng-container [ngTemplateOutlet]="statusTpl" [ngTemplateOutletContext]="{ $implicit: b }" /></div>
            <ng-container [ngTemplateOutlet]="actionsTpl" [ngTemplateOutletContext]="{ $implicit: b }" />
          </li>
        }
      </ul>
    }
  `,
})
export class BookingListComponent {
  readonly items = input.required<BookingResponse[]>();
  readonly mode = input<'mine' | 'approvals'>('mine');
  readonly busyId = input<string | null>(null);
  readonly act = output<BookingActionEvent>();

  protected readonly desktop = desktopSignal();

  protected caption(): string {
    return this.mode() === 'mine' ? 'Lịch đặt của tôi' : 'Yêu cầu chờ duyệt';
  }

  protected date(b: BookingResponse): string {
    return vnDisplayDate(b.startTime);
  }

  protected range(b: BookingResponse): string {
    return timeRange(b.startTime, b.endTime);
  }

  protected actionsFor(b: BookingResponse): ActionDef[] {
    if (this.mode() === 'approvals') return [APPROVE, REJECT];
    switch (b.status) {
      case 'PENDING':
        return [CANCEL];
      case 'CONFIRMED':
        return [CHECK_IN, CANCEL];
      case 'CHECKED_IN':
        return [CHECK_OUT];
      default:
        return [];
    }
  }
}
