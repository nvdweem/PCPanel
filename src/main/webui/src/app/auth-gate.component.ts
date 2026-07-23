import { ChangeDetectionStrategy, Component, effect, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { AuthStateService } from './services/auth-state.service';

/**
 * Full-screen block shown when the page has no valid session with the local API. Hosted at the app root
 * so it overlays regardless of route.
 *
 * Recovery is automatic: reopening PCPanel from the tray runs the bootstrap handshake, which sets the
 * session cookie browser-wide (cookies are shared across tabs of the same host). While the gate is up we
 * poll the gated status endpoint and, the moment it accepts us again, reload once into the working app.
 * We deliberately never reload blindly — a reload while the session is still invalid would just return
 * here, which is the "stuck reloading" the button used to cause.
 */
@Component({
  selector: 'app-auth-gate',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (authState.unauthenticated()) {
      <div class="gate">
        <div class="card">
          <h1>PCPanel is locked</h1>
          <p>This page isn't authorised to control PCPanel. For your security, access is only granted to
             the window PCPanel opens itself.</p>
          <p class="how">Open PCPanel from its <strong>system tray icon</strong> — this page unlocks itself
             once you do.</p>
          <button class="pc-btn primary" (click)="check()">Check again</button>
        </div>
      </div>
    }
  `,
  styles: [`
    .gate { position: fixed; inset: 0; z-index: 10000; display: flex; align-items: center; justify-content: center;
            background: rgba(0, 0, 0, 0.72); }
    .card { background: var(--panel, #1e1e24); border: 1px solid var(--line, #333); border-radius: var(--r-md, 10px);
            padding: 26px 28px; max-width: 420px; text-align: center; display: flex; flex-direction: column; gap: 12px; }
    h1 { font-size: 17px; color: var(--text-1, #eee); margin: 0; }
    p { font-size: 13px; color: var(--text-2, #bbb); margin: 0; line-height: 1.55; }
    .how { color: var(--text-1, #eee); }
    button { align-self: center; margin-top: 6px; }
  `],
})
export class AuthGateComponent implements OnDestroy {
  readonly authState = inject(AuthStateService);
  private readonly http = inject(HttpClient);
  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    // While locked out, watch for the session coming back (the user reopening PCPanel from the tray sets
    // a fresh cookie) and recover on our own. This cannot loop: only a 200 reloads, and a cookie valid
    // enough for a 200 also lets the WebSocket handshake through, so the reloaded page connects instead
    // of returning here; a still-invalid session stays 401 and the gate simply keeps waiting.
    effect(() => {
      if (this.authState.unauthenticated()) {
        this.timer ??= setInterval(() => this.check(), 2000);
      } else {
        this.stop();
      }
    });
  }

  /** Re-check the session now (also runs automatically every couple of seconds while locked out). */
  check(): void {
    this.http.get('/api/auth/status', { responseType: 'text' })
      .subscribe({ next: () => location.reload(), error: () => { /* still locked — keep waiting */ } });
  }

  private stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
