import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-pagination',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav aria-label="Phân trang" class="flex flex-wrap items-center justify-between gap-2">
        <p class="tabular-nums text-n-600">Trang {{ page() + 1 }} / {{ totalPages() }} · {{ totalElements() }} mục</p>
        <div class="flex gap-2">
          <button type="button" class="btn btn-secondary" [disabled]="!hasPrev()" (click)="pageChange.emit(page() - 1)">
            <app-icon name="chevron-left" />
            Trước
          </button>
          <button type="button" class="btn btn-secondary" [disabled]="!hasNext()" (click)="pageChange.emit(page() + 1)">
            Sau
            <app-icon name="chevron-right" />
          </button>
        </div>
      </nav>
    }
  `,
})
export class PaginationComponent {
  /** Zero-based, as the API returns it. */
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input(0);
  readonly pageChange = output<number>();

  protected readonly hasPrev = computed(() => this.page() > 0);
  protected readonly hasNext = computed(() => this.page() + 1 < this.totalPages());
}
