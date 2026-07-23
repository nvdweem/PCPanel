import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AuthStateService } from './services/auth-state.service';

/**
 * Full-screen block shown when the page has no valid session with the local API. Hosted at the app root
 * so it overlays regardless of route. Reopening PCPanel from the tray runs the bootstrap handshake,
 * which sets the session cookie browser-wide (cookies are shared across tabs of the same host), so
 * "Reload" then picks it up.
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
             the browser window PCPanel opens itself.</p>
          <p class="how">Open PCPanel from its <strong>system tray icon</strong>, then reload this page.</p>
          <button class="pc-btn primary" (click)="reload()">Reload</button>
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
export class AuthGateComponent {
  readonly authState = inject(AuthStateService);

  reload(): void {
    location.reload();
  }
}
