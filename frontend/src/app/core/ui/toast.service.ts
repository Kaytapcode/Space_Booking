import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'error' | 'success';
  message: string;
}

const AUTO_DISMISS_MS = 6000;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly list = signal<Toast[]>([]);
  readonly toasts = this.list.asReadonly();

  error(message: string): void {
    this.push('error', message);
  }

  success(message: string): void {
    this.push('success', message);
  }

  dismiss(id: number): void {
    this.list.update((all) => all.filter((t) => t.id !== id));
  }

  private push(kind: Toast['kind'], message: string): void {
    const id = this.nextId++;
    this.list.update((all) => [...all, { id, kind, message }]);
    setTimeout(() => this.dismiss(id), AUTO_DISMISS_MS);
  }
}
