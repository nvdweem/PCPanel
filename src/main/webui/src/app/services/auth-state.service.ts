import { Injectable, signal } from '@angular/core';

/**
 * Tracks whether this page holds a valid session with the local API. It flips to unauthenticated when
 * the API rejects a request with HTTP 401 (see {@link authInterceptor}) — which happens when the page
 * was opened outside the tray bootstrap handshake, or after the app restarted (a restart clears the
 * in-memory sessions, invalidating the cookie). The {@link AuthGateComponent} then blocks the UI and
 * tells the user to reopen PCPanel from the tray.
 */
@Injectable({ providedIn: 'root' })
export class AuthStateService {
  readonly unauthenticated = signal(false);

  markUnauthenticated(): void {
    this.unauthenticated.set(true);
  }
}
