import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import type { components } from '../../api/schema';
import { RoomsApi } from '../../core/api/rooms.service';
import { ToastService } from '../../core/ui/toast.service';
import { AsyncViewComponent } from '../../shared/async-view.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { IconComponent } from '../../shared/icon.component';
import { RoomFormDialogComponent } from './room-form-dialog.component';

type RoomResponse = components['schemas']['RoomResponse'];

@Component({
  selector: 'app-admin-rooms-page',
  imports: [AsyncViewComponent, ConfirmDialogComponent, IconComponent, RoomFormDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <h1 class="text-h1 font-semibold">Quản lý phòng</h1>
        <button type="button" class="btn btn-primary" (click)="editing.set('new')">
          <app-icon name="plus" />
          Thêm phòng
        </button>
      </div>

      <app-async-view
        [loading]="loading()"
        [error]="error()"
        [empty]="items().length === 0"
        emptyMessage="Chưa có phòng nào. Thêm phòng đầu tiên."
        (retry)="load()"
      >
        <div class="panel overflow-x-auto">
          <table class="w-full">
            <caption class="sr-only">
              Danh sách phòng
            </caption>
            <thead class="border-b border-n-200 bg-n-50">
              <tr>
                <th scope="col" class="th">Tên phòng</th>
                <th scope="col" class="th">Vị trí</th>
                <th scope="col" class="th">Sức chứa</th>
                <th scope="col" class="th">Thiết bị</th>
                <th scope="col" class="th">Duyệt</th>
                <th scope="col" class="th">Trạng thái</th>
                <th scope="col" class="th">Thao tác</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-n-200">
              @for (r of items(); track r.id) {
                <tr [class.text-n-600]="!r.active">
                  <td class="td font-medium">{{ r.name }}</td>
                  <td class="td">{{ r.location }}</td>
                  <td class="td tabular-nums">{{ r.capacity }}</td>
                  <td class="td">{{ r.equipments.join(', ') || '-' }}</td>
                  <td class="td">{{ r.requiresApproval ? 'Cần duyệt' : 'Tự xác nhận' }}</td>
                  <td class="td">{{ r.active ? 'Hoạt động' : 'Ngừng hoạt động' }}</td>
                  <td class="td">
                    <div class="flex flex-wrap gap-2">
                      <button type="button" class="btn btn-secondary" [attr.aria-label]="'Sửa phòng ' + r.name" (click)="editing.set(r)">
                        Sửa
                      </button>
                      @if (r.active) {
                        <button type="button" class="btn btn-secondary" [attr.aria-label]="'Ngừng hoạt động phòng ' + r.name" (click)="deactivating.set(r)">
                          Ngừng
                        </button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </app-async-view>
    </div>

    @if (editing(); as e) {
      <app-room-form-dialog [room]="e === 'new' ? null : e" (saved)="onSaved()" (dismissed)="editing.set(null)" />
    }
    @if (deactivating(); as r) {
      <app-confirm-dialog
        heading="Ngừng hoạt động phòng"
        [message]="'Phòng ' + r.name + ' sẽ không còn hiện cho người đặt. Lịch đã đặt được giữ nguyên.'"
        confirmLabel="Ngừng hoạt động"
        [danger]="true"
        [busy]="busy()"
        (confirmed)="deactivate(r)"
        (dismissed)="deactivating.set(null)"
      />
    }
  `,
})
export class AdminRoomsPage {
  private readonly api = inject(RoomsApi);
  private readonly toast = inject(ToastService);

  protected readonly items = signal<RoomResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly busy = signal(false);
  protected readonly editing = signal<RoomResponse | 'new' | null>(null);
  protected readonly deactivating = signal<RoomResponse | null>(null);
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
    this.sub = this.api.search({ includeInactive: true }).subscribe({
      next: (rooms) => {
        this.items.set(rooms);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  protected onSaved(): void {
    this.editing.set(null);
    this.load(true);
  }

  protected deactivate(r: RoomResponse): void {
    this.busy.set(true);
    this.api.deactivate(r.id).subscribe({
      next: () => {
        this.toast.success('Đã ngừng hoạt động phòng.');
        this.busy.set(false);
        this.deactivating.set(null);
        this.load(true);
      },
      // ROOM_HAS_FUTURE_BOOKINGS and the rest are toasted by the interceptor.
      error: () => {
        this.busy.set(false);
        this.deactivating.set(null);
      },
    });
  }
}
