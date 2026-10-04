import { effect, inject, Injectable, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { HistoryDto } from '../models/generated/backend.types';
import { DeviceStateService } from './device-state.service';
import { ToastService } from '../ui';

/**
 * Undo / redo of configuration changes (assignments, lighting, settings). The backend keeps the history; this
 * mirrors whether there is something to undo or redo and runs the steps.
 */
@Injectable({ providedIn: 'root' })
export class HistoryService {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);

  readonly canUndo = signal(false);
  readonly canRedo = signal(false);
  /** Bumped on every undo or redo that changed something, for the page flash. */
  readonly flashes = signal(0);
  /** Bumped once an undo or redo has reached the page, so pages holding an edited copy can reload it. */
  readonly applied = signal(0);
  private timer?: ReturnType<typeof setTimeout>;
  /** An undo or redo is done on the backend; its restored panel state is on its way over the socket. */
  private awaitingState = false;
  /** The message for the undo or redo waiting on its restored state, shown once the page has it. */
  private announcement = '';
  private fallback?: ReturnType<typeof setTimeout>;
  /** Edits pages have not saved yet (their saves are debounced); each resolves true when it saved something. */
  private readonly pending = new Set<() => Promise<boolean>>();

  private readonly state = inject(DeviceStateService);

  constructor() {
    // The backend announces every change of what can be undone; (re)connecting re-reads it in full.
    const state = this.state;
    effect(() => {
      const h = state.history();
      if (h) untracked(() => this.set(h));
    });
    // The restored state has arrived: let pages re-seed from it.
    effect(() => {
      state.connectEpoch();
      untracked(() => {
        if (this.awaitingState) this.settle();
        else this.refresh(); // reconnected: what changed meanwhile was not announced to this page
      });
    });
    // Leaving a page ends the edit made on it.
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(() => this.checkpoint());
  }

  /**
   * A page that saves after a delay registers how to save now. An undo or redo saves those edits first, so it takes
   * back the edit in progress along with what was saved of it, not the step before. Returns the unregister function.
   */
  registerPending(saveNow: () => Promise<boolean>): () => void {
    this.pending.add(saveNow);
    return () => this.pending.delete(saveNow);
  }

  /** The user moved away from what they were editing: the next change becomes a new undo step. */
  checkpoint(): void {
    this.http.post('/api/settings/history/checkpoint', {}).subscribe({ error: () => undefined });
  }

  private settle(): void {
    this.awaitingState = false;
    if (this.fallback) clearTimeout(this.fallback);
    this.applied.update(n => n + 1);
    // Flash once the pages have re-seeded and drawn the restored state (a settings page re-reads it first), so the
    // flash marks the change rather than coming before it.
    const message = this.announcement;
    this.announcement = '';
    setTimeout(() => {
      this.flashes.update(n => n + 1);
      if (message) this.toast.show(message, { kind: 'success' });
    }, 150);
  }

  /** Re-reads the history once the backend has written the latest change. */
  refreshSoon(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.refresh(), 1500);
  }

  refresh(): void {
    this.http.get<HistoryDto>('/api/settings/history').subscribe({ next: h => this.set(h), error: () => undefined });
  }

  undo(): void { this.step('undo', 'Undone', 'Nothing to undo'); }

  redo(): void { this.step('redo', 'Redone', 'Nothing to redo'); }

  private async step(kind: 'undo' | 'redo', done: string, nothing: string): Promise<void> {
    const saved = (await Promise.all([...this.pending].map(f => f().catch(() => false)))).some(Boolean);
    // A just-saved edit is something to undo, and leaves nothing to redo, before the history has been re-read.
    const possible = saved ? kind === 'undo' : kind === 'undo' ? this.canUndo() : this.canRedo();
    if (!possible) {
      this.toast.show(nothing, { kind: 'info' });
      return;
    }
    const epoch = this.state.connectEpoch();
    this.http.post<HistoryDto>(`/api/settings/${kind}`, {}).subscribe({
      next: h => {
        this.set(h);
        const what = h.changed ?? [];
        this.announcement = what.length ? `${done}: ${what.join(', ')}` : done;
        // Pages re-seed once the restored panel state arrives over the socket (or shortly after, without a panel).
        // It is often in before this reply: then now.
        if (this.state.connectEpoch() !== epoch) {
          this.settle();
          return;
        }
        this.awaitingState = true;
        if (this.fallback) clearTimeout(this.fallback);
        this.fallback = setTimeout(() => this.settle(), 1000);
      },
      error: () => this.toast.show(`Could not ${kind}`, { kind: 'error' }),
    });
  }

  private set(h: Pick<HistoryDto, 'canUndo' | 'canRedo'>): void {
    this.canUndo.set(h.canUndo);
    this.canRedo.set(h.canRedo);
  }
}
