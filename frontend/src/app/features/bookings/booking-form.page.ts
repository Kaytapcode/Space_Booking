import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, catchError, debounceTime, filter, merge, of, switchMap } from 'rxjs';
import type { components } from '../../api/schema';
import { BookingsApi } from '../../core/api/bookings.service';
import { RoomSearch, RoomsApi } from '../../core/api/rooms.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/ui/toast.service';
import { endAfterStartValidator } from '../../core/validators/time-range.validator';
import { FieldComponent } from '../../shared/field.component';
import { controlError, notBlank } from '../../shared/format';
import { toUtcIso, vnToday } from '../../shared/vn-time';

type RoomResponse = components['schemas']['RoomResponse'];

const EMPLOYEE_MAX_MINUTES = 120;
const TOKEN = /^[a-z0-9-]{1,24}$/;
const minutes = (t: string): number => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));

/** The API rejects a start before the current minute; mirror it so the form blocks it first. */
const startNotPast: ValidatorFn = (g: AbstractControl): ValidationErrors | null => {
  const { date, startTime } = g.value as { date: string; startTime: string };
  if (!date || !startTime) return null;
  const start = Date.parse(`${date}T${startTime}:00+07:00`);
  return start < Math.floor(Date.now() / 60_000) * 60_000 ? { startInPast: true } : null;
};

const maxDuration =
  (limit: number): ValidatorFn =>
  (g: AbstractControl): ValidationErrors | null => {
    const { startTime, endTime } = g.value as { startTime: string; endTime: string };
    if (!startTime || !endTime) return null;
    return minutes(endTime) - minutes(startTime) > limit ? { durationExceeded: true } : null;
  };

@Component({
  selector: 'app-booking-form-page',
  imports: [ReactiveFormsModule, FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto flex max-w-xl flex-col gap-4">
      <h1 class="text-h1 font-semibold">Đặt phòng</h1>

      <form [formGroup]="filters" class="panel flex flex-col gap-4 p-4" aria-label="Tìm phòng">
        <h2 class="text-h3 font-semibold">Tìm phòng</h2>
        <div class="grid gap-4 sm:grid-cols-2">
          <app-field label="Sức chứa tối thiểu" controlId="bk-cap">
            <input id="bk-cap" type="number" min="1" inputmode="numeric" class="input" formControlName="minCapacity" />
          </app-field>
          <app-field label="Thiết bị" controlId="bk-equip" hint="Ví dụ: tv, whiteboard">
            <input id="bk-equip" type="text" class="input" formControlName="equipments" aria-describedby="bk-equip-msg" />
          </app-field>
        </div>
        <label class="flex min-h-11 items-center gap-2 lg:min-h-9">
          <input type="checkbox" class="size-5 accent-primary-700 lg:size-4" formControlName="onlyAvailable" />
          Chỉ hiện phòng còn trống trong khung giờ đã chọn
        </label>
      </form>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="panel flex flex-col gap-4 p-4">
        <app-field label="Phòng" controlId="bk-room" [error]="roomError()">
          <select
            id="bk-room"
            class="input"
            formControlName="roomId"
            aria-describedby="bk-room-msg"
            [attr.aria-invalid]="roomError() ? 'true' : null"
          >
            <option value="">Chọn phòng</option>
            @for (r of rooms(); track r.id) {
              <option [value]="r.id">{{ r.name }} · {{ r.capacity }} chỗ</option>
            }
          </select>
        </app-field>
        @if (roomsFailed()) {
          <div class="flex items-center gap-2 text-danger-700" role="alert">
            Không tải được danh sách phòng.
            <button type="button" class="btn btn-secondary" (click)="refresh$.next()">Thử lại</button>
          </div>
        } @else if (roomsLoaded() && rooms().length === 0) {
          <p class="text-n-600">Không có phòng phù hợp. Bỏ bớt bộ lọc.</p>
        }

        <app-field label="Nội dung" controlId="bk-title" [error]="titleError()">
          <input
            id="bk-title"
            type="text"
            maxlength="255"
            class="input"
            formControlName="title"
            aria-describedby="bk-title-msg"
            [attr.aria-invalid]="titleError() ? 'true' : null"
          />
        </app-field>

        <app-field label="Ngày" controlId="bk-date" [error]="controlErr('date', { required: 'Chọn ngày.' })">
          <input
            id="bk-date"
            type="date"
            class="input"
            formControlName="date"
            aria-describedby="bk-date-msg"
            [attr.min]="today"
          />
        </app-field>

        <div class="grid gap-4 sm:grid-cols-2">
          <app-field label="Giờ bắt đầu" controlId="bk-start" [error]="startError()">
            <input
              id="bk-start"
              type="time"
              step="1800"
              class="input tabular-nums"
              formControlName="startTime"
              aria-describedby="bk-start-msg"
              [attr.aria-invalid]="startError() ? 'true' : null"
            />
          </app-field>
          <app-field label="Giờ kết thúc" controlId="bk-end" [error]="endError()" [hint]="isEmployee ? 'Nhân viên đặt tối đa 2 giờ mỗi lượt.' : null">
            <input
              id="bk-end"
              type="time"
              step="1800"
              class="input tabular-nums"
              formControlName="endTime"
              aria-describedby="bk-end-msg"
              [attr.aria-invalid]="endError() ? 'true' : null"
            />
          </app-field>
        </div>

        <button type="submit" class="btn btn-primary w-full" [disabled]="busy()">Đặt phòng</button>
      </form>
    </div>
  `,
})
export class BookingFormPage {
  private readonly bookings = inject(BookingsApi);
  private readonly roomsApi = inject(RoomsApi);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly today = vnToday();
  protected readonly isEmployee = this.auth.role() === 'EMPLOYEE';
  protected readonly busy = signal(false);
  protected readonly conflict = signal(false);
  private readonly submitted = signal(false);

  protected readonly rooms = signal<RoomResponse[]>([]);
  protected readonly roomsLoaded = signal(false);
  protected readonly roomsFailed = signal(false);
  protected readonly refresh$ = new Subject<void>();

  protected readonly filters = this.fb.group({
    minCapacity: this.fb.control<number | null>(null),
    equipments: [''],
    onlyAvailable: [false],
  });

  protected readonly form = this.fb.group(
    {
      roomId: ['', Validators.required],
      title: ['', [Validators.required, Validators.maxLength(255), notBlank]],
      date: [this.today, Validators.required],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
    },
    {
      validators: [
        endAfterStartValidator(),
        startNotPast,
        ...(this.isEmployee ? [maxDuration(EMPLOYEE_MAX_MINUTES)] : []),
      ],
    },
  );

  constructor() {
    const qp = inject(ActivatedRoute).snapshot.queryParamMap;
    this.form.patchValue({
      roomId: qp.get('roomId') ?? '',
      date: qp.get('date') ?? this.today,
      startTime: qp.get('start') ?? '',
      endTime: qp.get('end') ?? '',
    });

    const c = this.form.controls;
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.conflict.set(false));

    // Room search: filters re-query after a pause; time changes only matter for the availability filter.
    merge(
      of(null),
      this.refresh$,
      this.filters.valueChanges.pipe(debounceTime(300)),
      merge(c.date.valueChanges, c.startTime.valueChanges, c.endTime.valueChanges).pipe(
        filter(() => this.filters.controls.onlyAvailable.value),
        debounceTime(300),
      ),
    )
      .pipe(
        switchMap(() => this.roomsApi.search(this.query()).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((list) => {
        this.roomsFailed.set(list === null);
        if (!list) return;
        this.rooms.set(list);
        this.roomsLoaded.set(true);
        if (c.roomId.value && !list.some((r) => r.id === c.roomId.value)) c.roomId.setValue('');
      });
  }

  private query(): RoomSearch {
    const { minCapacity, equipments, onlyAvailable } = this.filters.getRawValue();
    const { date, startTime, endTime } = this.form.getRawValue();
    const query: RoomSearch = {};
    if (minCapacity && minCapacity >= 1) query.minCapacity = minCapacity;
    const tokens = [...new Set(equipments.toLowerCase().split(/[\s,]+/).filter((t) => TOKEN.test(t)))];
    if (tokens.length) query.equipments = tokens;
    if (onlyAvailable && date && startTime && endTime && endTime > startTime) {
      query.availableFrom = toUtcIso(date, startTime);
      query.availableTo = toUtcIso(date, endTime);
    }
    return query;
  }

  protected controlErr(name: 'roomId' | 'title' | 'date' | 'startTime' | 'endTime', messages: Record<string, string>): string | null {
    return controlError(this.form.controls[name], messages);
  }

  protected roomError(): string | null {
    return this.controlErr('roomId', { required: 'Chọn phòng.' });
  }

  protected titleError(): string | null {
    return this.controlErr('title', {
      required: 'Nhập nội dung cuộc họp.',
      blank: 'Nhập nội dung cuộc họp.',
      maxlength: 'Nội dung tối đa 255 ký tự.',
    });
  }

  protected startError(): string | null {
    const own = this.controlErr('startTime', { required: 'Chọn giờ bắt đầu.' });
    if (own) return own;
    if (this.showGroupErrors('startTime') && this.form.errors?.['startInPast']) {
      return 'Giờ bắt đầu đã qua. Chọn giờ khác.';
    }
    return this.conflict() ? 'Khung giờ này đã có người đặt. Chọn giờ khác.' : null;
  }

  protected endError(): string | null {
    const own = this.controlErr('endTime', { required: 'Chọn giờ kết thúc.' });
    if (own) return own;
    if (!this.showGroupErrors('endTime')) return null;
    const errors = this.form.errors;
    if (errors?.['endBeforeStart']) return 'Giờ kết thúc phải sau giờ bắt đầu.';
    if (errors?.['durationExceeded']) return 'Nhân viên đặt tối đa 2 giờ mỗi lượt. Rút ngắn thời gian.';
    return null;
  }

  private showGroupErrors(name: 'startTime' | 'endTime'): boolean {
    const ctrl = this.form.controls[name];
    return this.submitted() || ctrl.touched || ctrl.dirty;
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { roomId, title, date, startTime, endTime } = this.form.getRawValue();
    this.busy.set(true);
    this.bookings
      .create({ roomId, title: title.trim(), startTime: toUtcIso(date, startTime), endTime: toUtcIso(date, endTime) })
      .subscribe({
        next: (b) => {
          this.toast.success(b.status === 'PENDING' ? 'Đã gửi yêu cầu. Chờ quản lý duyệt.' : 'Đã đặt phòng.');
          void this.router.navigateByUrl('/my-bookings');
        },
        error: (err: unknown) => {
          this.busy.set(false);
          // The interceptor already toasts; the conflict also gets a hint on the time field.
          if (err instanceof HttpErrorResponse && (err.error as { code?: string } | null)?.code === 'BOOKING_CONFLICT') {
            this.conflict.set(true);
          }
        },
      });
  }
}
