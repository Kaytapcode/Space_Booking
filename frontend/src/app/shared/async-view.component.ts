import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from './icon.component';

/** Loading skeleton, error with retry, empty state, or the projected content (DESIGN.md "States"). */
@Component({
  selector: 'app-async-view',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.aria-busy]': 'loading()' },
  template: `
    @if (loading()) {
      <div role="status" aria-label="Đang tải" class="flex flex-col gap-2">
        @for (i of skeletonRows; track i) {
          <div class="animate-pulse rounded-md bg-n-200" [class]="skeleton() === 'card' ? 'h-28' : 'h-10'"></div>
        }
      </div>
    } @else if (error()) {
      <div role="alert" class="panel flex flex-col items-center gap-3 p-6 text-center">
        <span class="text-danger-700"><app-icon name="alert-circle" /></span>
        <p>Không tải được dữ liệu. Thử lại.</p>
        <button type="button" class="btn btn-secondary" (click)="retry.emit()">Thử lại</button>
      </div>
    } @else if (empty()) {
      <div class="panel flex flex-col items-center gap-3 p-6 text-center">
        <span class="text-n-500"><app-icon name="inbox" /></span>
        <p>{{ emptyMessage() }}</p>
        <ng-content select="[empty-action]" />
      </div>
    } @else {
      <ng-content />
    }
  `,
})
export class AsyncViewComponent {
  readonly loading = input(false);
  readonly error = input(false);
  readonly empty = input(false);
  readonly emptyMessage = input('Chưa có dữ liệu.');
  readonly skeleton = input<'row' | 'card'>('row');
  readonly retry = output<void>();
  protected readonly skeletonRows = [0, 1, 2, 3, 4];
}
