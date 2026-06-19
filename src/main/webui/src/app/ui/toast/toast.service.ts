import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'info' | 'warn' | 'error';

export interface Toast {
  id: number;
  message: string;
  sub?: string;
  kind: ToastKind;
}

/** Lightweight non-blocking toast queue (profile switches, version notices). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  show(message: string, opts: { sub?: string; kind?: ToastKind; timeout?: number } = {}): number {
    const id = ++this.seq;
    const toast: Toast = { id, message, sub: opts.sub, kind: opts.kind ?? 'info' };
    this.toasts.update(list => [...list, toast]);
    const timeout = opts.timeout ?? 3200;
    if (timeout > 0) setTimeout(() => this.dismiss(id), timeout);
    return id;
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }
}
