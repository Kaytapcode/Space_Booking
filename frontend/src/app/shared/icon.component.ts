import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// Lucide (ISC) paths, 24x24 viewBox, inlined to avoid a dependency (P5-2). Primitives are converted to path data.
const CIRCLE_10 = 'M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0z';

export const ICONS = {
  clock: [CIRCLE_10, 'M12 6v6l4 2'],
  'calendar-check': [
    'M8 2v4',
    'M16 2v4',
    'M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
    'M3 10h18',
    'm9 16 2 2 4-4',
  ],
  'log-in': ['M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4', 'm10 17 5-5-5-5', 'M15 12H3'],
  check: ['M20 6 9 17l-5-5'],
  x: ['M18 6 6 18', 'm6 6 12 12'],
  'user-x': ['M2 21a8 8 0 0 1 11.873-7', 'M5 8a5 5 0 1 0 10 0a5 5 0 1 0-10 0z', 'm17 17 5 5', 'm22 17-5 5'],
  'chevron-left': ['m15 18-6-6 6-6'],
  'chevron-right': ['m9 18 6-6-6-6'],
  search: ['M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0z', 'm21 21-4.3-4.3'],
  users: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2',
    'M5 7a4 4 0 1 0 8 0a4 4 0 1 0-8 0z',
    'M22 21v-2a4 4 0 0 0-3-3.87',
    'M16 3.13a4 4 0 0 1 0 7.75',
  ],
  plus: ['M5 12h14', 'M12 5v14'],
  'log-out': ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  'alert-circle': [CIRCLE_10, 'M12 8v4', 'M12 16h.01'],
  inbox: [
    'M22 12h-6l-2 3h-4l-2-3H2',
    'M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z',
  ],
} as const;

export type IconName = keyof typeof ICONS;

/** Decorative by default (`aria-hidden`); icon-only buttons must carry their own `aria-label`. 20px mobile, 16px desktop. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex shrink-0' },
  template: `
    <svg
      class="size-5 lg:size-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @for (d of paths(); track d) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
})
export class IconComponent {
  readonly name = input.required<string>();

  protected paths(): readonly string[] {
    return ICONS[this.name() as IconName] ?? [];
  }
}
