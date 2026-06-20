import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'info' | 'warn' | 'error';

export interface Toast {
  id: number;
  message: string;
  sub?: string;
  kind: ToastKind;
  /** Optional call-to-action link (e.g. a download page); rendered as an anchor in the toast. */
  href?: string;
  /** Label for the {@link href} link; defaults to "Open". */
  action?: string;
}

/** Lightweight non-blocking toast queue (profile switches, version notices). */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  show(message: string, opts: { sub?: string; kind?: ToastKind; timeout?: number; href?: string; action?: string } = {}): number {
    const id = ++this.seq;
    const toast: Toast = { id, message, sub: opts.sub, kind: opts.kind ?? 'info', href: opts.href, action: opts.action };
    this.toasts.update(list => [...list, toast]);
    const timeout = opts.timeout ?? 3200;
    if (timeout > 0) setTimeout(() => this.dismiss(id), timeout);
    return id;
  }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }
}
