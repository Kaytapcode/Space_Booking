import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';

let nextId = 0;

/**
 * Modal built on the native `<dialog>`: showModal() gives the focus trap and inert background, Esc fires
 * `cancel`. The element that opened it gets focus back on destroy. Render it with `@if` to open it.
 */
@Component({
  selector: 'app-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dlg
      class="m-auto max-h-[calc(100dvh-2rem)] w-[min(28rem,calc(100vw-2rem))] overflow-auto rounded-lg border border-n-200 bg-n-0 p-0 text-n-900 shadow-sm backdrop:bg-n-900/40"
      [attr.aria-labelledby]="titleId"
      (cancel)="onCancel($event)"
    >
      <div class="flex flex-col gap-4 p-4 lg:p-6">
        <h2 [id]="titleId" class="text-h2 font-semibold">{{ heading() }}</h2>
        <ng-content />
      </div>
    </dialog>
  `,
})
export class DialogComponent {
  readonly heading = input.required<string>();
  readonly dismissed = output<void>();

  protected readonly titleId = `dlg-title-${nextId++}`;
  private readonly dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  constructor() {
    const opener = inject(DOCUMENT).activeElement as HTMLElement | null;
    afterNextRender(() => this.dlg().nativeElement.showModal());
    inject(DestroyRef).onDestroy(() => {
      const el = this.dlg().nativeElement;
      if (el.open) el.close();
      opener?.focus();
    });
  }

  protected onCancel(event: Event): void {
    event.preventDefault();
    this.dismissed.emit();
  }
}
