import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogComponent } from './dialog.component';
import { FieldComponent } from './field.component';
import { controlError, notBlank } from './format';

/** Confirm/cancel dialog; with `withReason` the confirm emits a 1-255 char non-blank reason (reject flow). */
@Component({
  selector: 'app-confirm-dialog',
  imports: [ReactiveFormsModule, DialogComponent, FieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialog [heading]="heading()" (dismissed)="dismissed.emit()">
      <p class="text-n-600">{{ message() }}</p>
      @if (withReason()) {
        <app-field label="Lý do" controlId="confirm-reason" [error]="reasonError()" hint="Tối đa 255 ký tự.">
          <textarea
            id="confirm-reason"
            class="input"
            rows="3"
            maxlength="255"
            aria-describedby="confirm-reason-msg"
            [attr.aria-invalid]="reasonError() ? 'true' : null"
            [formControl]="reason"
          ></textarea>
        </app-field>
      }
      <div class="flex justify-end gap-2">
        <button type="button" class="btn btn-secondary" (click)="dismissed.emit()">Quay lại</button>
        <button
          type="button"
          class="btn"
          [class]="danger() ? 'btn-danger' : 'btn-primary'"
          [disabled]="busy()"
          (click)="submit()"
        >
          {{ confirmLabel() }}
        </button>
      </div>
    </app-dialog>
  `,
})
export class ConfirmDialogComponent {
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('Xác nhận');
  readonly danger = input(false);
  readonly withReason = input(false);
  readonly busy = input(false);
  readonly confirmed = output<string>();
  readonly dismissed = output<void>();

  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(255), notBlank],
  });

  protected reasonError(): string | null {
    return controlError(this.reason, { required: 'Nhập lý do.', blank: 'Nhập lý do.', maxlength: 'Lý do tối đa 255 ký tự.' });
  }

  protected submit(): void {
    if (this.withReason()) {
      this.reason.markAsTouched();
      if (this.reason.invalid) return;
    }
    this.confirmed.emit(this.reason.value.trim());
  }
}
