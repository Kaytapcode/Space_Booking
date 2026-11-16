import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Label above, control projected, hint or error below. The control must carry `id=controlId` and `aria-describedby=controlId-msg`. */
@Component({
  selector: 'app-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="flex flex-col gap-1">
      <label [for]="controlId()" class="font-medium">{{ label() }}</label>
      <ng-content />
      <p
        [id]="controlId() + '-msg'"
        [class.hidden]="!(error() || hint())"
        [class]="error() ? 'text-danger-700' : 'text-n-600'"
      >
        {{ error() || hint() }}
      </p>
    </div>
  `,
})
export class FieldComponent {
  readonly label = input.required<string>();
  readonly controlId = input.required<string>();
  readonly error = input<string | null>(null);
  readonly hint = input<string | null>(null);
}
