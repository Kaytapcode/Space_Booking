import { DestroyRef, Signal, inject, signal } from '@angular/core';

/** True at the Tailwind `lg` breakpoint (>= 1024px). Call in an injection context. */
export function desktopSignal(): Signal<boolean> {
  const mq = window.matchMedia('(min-width: 1024px)');
  const state = signal(mq.matches);
  const onChange = () => state.set(mq.matches);
  mq.addEventListener('change', onChange);
  inject(DestroyRef).onDestroy(() => mq.removeEventListener('change', onChange));
  return state.asReadonly();
}
