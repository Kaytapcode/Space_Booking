import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../core/ui/toast.service';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-toast-outlet',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'pointer-events-none fixed inset-x-4 bottom-4 z-50 flex flex-col items-end gap-2 lg:left-auto lg:right-6' },
  template: `
    <div class="flex w-full max-w-sm flex-col gap-2" aria-live="polite" aria-atomic="false">
      @for (t of toasts.toasts(); track t.id) {
        <div
          class="pointer-events-auto flex min-h-11 items-start gap-2 rounded-lg border border-n-200 bg-n-0 p-3 shadow-sm"
          [class.text-danger-700]="t.kind === 'error'"
          [class.text-n-900]="t.kind === 'success'"
          [attr.role]="t.kind === 'error' ? 'alert' : 'status'"
        >
          <app-icon [name]="t.kind === 'error' ? 'alert-circle' : 'check'" class="mt-0.5" />
          <p class="flex-1">{{ t.message }}</p>
          <button
            type="button"
            class="-m-2 flex size-11 items-center justify-center rounded-md text-n-600 hover:bg-n-100 lg:size-9"
            aria-label="Đóng thông báo"
            (click)="toasts.dismiss(t.id)"
          >
            <app-icon name="x" />
          </button>
        </div>
      }
    </div>
  `,
})
export class ToastOutletComponent {
  protected readonly toasts = inject(ToastService);
}
