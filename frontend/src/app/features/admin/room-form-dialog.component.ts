import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import type { components } from '../../api/schema';
import { RoomsApi } from '../../core/api/rooms.service';
import { ToastService } from '../../core/ui/toast.service';
import { DialogComponent } from '../../shared/dialog.component';
import { FieldComponent } from '../../shared/field.component';
import { controlError, notBlank } from '../../shared/format';

type RoomResponse = components['schemas']['RoomResponse'];

const TOKEN = /^[a-z0-9-]{1,24}$/;
const MAX_EQUIPMENTS = 20;

function parseTokens(text: string): string[] {
  return text.split(/[\s,]+/).filter(Boolean);
}

/** Contract rules: tokens `^[a-z0-9-]{1,24}$`, unique, at most 20. */
function equipmentsValidator(control: AbstractControl): ValidationErrors | null {
  const tokens = parseTokens(String(control.value ?? ''));
  if (tokens.some((t) => !TOKEN.test(t))) return { token: true };
  if (new Set(tokens).size !== tokens.length) return { duplicate: true };
  return tokens.length > MAX_EQUIPMENTS ? { tooMany: true } : null;
}

/** Create (room = null) or edit a room. Emits `saved` after the API call succeeds. */
@Component({
  selector: 'app-room-form-dialog',
  imports: [ReactiveFormsModule, DialogComponent, FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [heading]="room() ? 'Sửa phòng' : 'Thêm phòng'" (dismissed)="dismissed.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="flex flex-col gap-4">
        <app-field label="Tên phòng" controlId="rf-name" [error]="error('name')">
          <input id="rf-name" type="text" maxlength="100" class="input" formControlName="name" aria-describedby="rf-name-msg" [attr.aria-invalid]="error('name') ? 'true' : null" />
        </app-field>
        <app-field label="Vị trí" controlId="rf-location" [error]="error('location')">
          <input id="rf-location" type="text" maxlength="255" class="input" formControlName="location" aria-describedby="rf-location-msg" [attr.aria-invalid]="error('location') ? 'true' : null" />
        </app-field>
        <app-field label="Sức chứa" controlId="rf-capacity" [error]="error('capacity')">
          <input id="rf-capacity" type="number" min="1" max="1000" inputmode="numeric" class="input" formControlName="capacity" aria-describedby="rf-capacity-msg" [attr.aria-invalid]="error('capacity') ? 'true' : null" />
        </app-field>
        <app-field label="Thiết bị" controlId="rf-equip" hint="Ngăn cách bằng dấu phẩy. Ví dụ: tv, whiteboard" [error]="error('equipments')">
          <input id="rf-equip" type="text" class="input" formControlName="equipments" aria-describedby="rf-equip-msg" [attr.aria-invalid]="error('equipments') ? 'true' : null" />
        </app-field>
        <label class="flex min-h-11 items-center gap-2 lg:min-h-9">
          <input type="checkbox" class="size-5 accent-primary-700 lg:size-4" formControlName="requiresApproval" />
          Cần quản lý duyệt
        </label>
        @if (room()) {
          <label class="flex min-h-11 items-center gap-2 lg:min-h-9">
            <input type="checkbox" class="size-5 accent-primary-700 lg:size-4" formControlName="active" />
            Đang hoạt động
          </label>
        }
        <div class="flex justify-end gap-2">
          <button type="button" class="btn btn-secondary" (click)="dismissed.emit()">Quay lại</button>
          <button type="submit" class="btn btn-primary" [disabled]="busy()">Lưu</button>
        </div>
      </form>
    </app-dialog>
  `,
})
export class RoomFormDialogComponent implements OnInit {
  private readonly api = inject(RoomsApi);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly room = input<RoomResponse | null>(null);
  readonly saved = output<void>();
  readonly dismissed = output<void>();

  protected readonly busy = signal(false);
  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100), notBlank]],
    location: ['', [Validators.required, Validators.maxLength(255), notBlank]],
    capacity: this.fb.control<number | null>(null, [Validators.required, Validators.min(1), Validators.max(1000)]),
    equipments: ['', equipmentsValidator],
    requiresApproval: [false],
    active: [true],
  });

  ngOnInit(): void {
    const r = this.room();
    if (r) this.form.patchValue({ ...r, equipments: r.equipments.join(', ') });
  }

  protected error(name: 'name' | 'location' | 'capacity' | 'equipments'): string | null {
    return controlError(this.form.controls[name], {
      required: 'Trường này là bắt buộc.',
      blank: 'Trường này là bắt buộc.',
      maxlength: 'Quá dài. Rút gọn lại.',
      min: 'Sức chứa từ 1 đến 1000.',
      max: 'Sức chứa từ 1 đến 1000.',
      token: 'Mỗi thiết bị gồm a-z, 0-9, dấu gạch ngang, tối đa 24 ký tự.',
      duplicate: 'Thiết bị bị trùng.',
      tooMany: 'Tối đa 20 thiết bị.',
      taken: 'Tên phòng đã tồn tại. Đổi tên khác.',
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: components['schemas']['RoomRequest'] = {
      name: v.name.trim(),
      location: v.location.trim(),
      capacity: v.capacity as number,
      equipments: parseTokens(v.equipments),
      requiresApproval: v.requiresApproval,
    };
    const r = this.room();
    if (r) body.active = v.active;
    this.busy.set(true);
    (r ? this.api.update(r.id, body) : this.api.create(body)).subscribe({
      next: () => {
        this.toast.success(r ? 'Đã lưu phòng.' : 'Đã thêm phòng.');
        this.saved.emit();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        if (err instanceof HttpErrorResponse && (err.error as { code?: string } | null)?.code === 'ROOM_NAME_ALREADY_EXISTS') {
          this.form.controls.name.setErrors({ taken: true });
          this.form.controls.name.markAsTouched();
        }
      },
    });
  }
}
