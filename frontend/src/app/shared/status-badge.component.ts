import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from './icon.component';
import { BookingStatus, statusBadge } from './status-badge';

/** Tint, dot, icon and label together: status is never carried by colour alone. */
@Component({
  selector: 'app-status-badge',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  template: `
    <span class="inline-flex items-center gap-1 rounded-sm px-2 py-0.5 font-medium" [class]="badge().classes">
      <span class="size-2 rounded-full bg-(--dot)" aria-hidden="true"></span>
      <app-icon [name]="badge().icon" />
      {{ badge().label }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<BookingStatus>();
  protected readonly badge = computed(() => statusBadge(this.status()));
}
