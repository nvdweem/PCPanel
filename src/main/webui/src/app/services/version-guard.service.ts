import { Injectable } from '@angular/core';

/**
 * Reloads the page when the backend version changes underneath an open tab.
 *
 * The frontend bundle ships with (and is served by) the backend, so when the backend restarts on a new
 * version — e.g. after an auto-update — the frontend already loaded in the tab is stale. The websocket
 * reconnects on its own, but that leaves the old bundle running against the new backend (stale version in
 * the footer, possibly an incompatible command catalog) until a manual refresh. This guard closes that gap:
 * on every (re)connect it reads the live backend version and, if it differs from the one the page loaded
 * with, reloads to pull the matching frontend.
 *
 * `index.html` is served `no-cache` (see {@code StaticCacheControl}), so the reload fetches the new bundle
 * rather than re-serving the stale one — the mismatch resolves in a single reload and cannot loop.
 */
@Injectable({ providedIn: 'root' })
export class VersionGuardService {
  /** The backend version this page was loaded with; set on the first successful connect. */
  private baseline: string | null = null;

  /** Call on every websocket (re)connect. */
  async checkOnConnect(): Promise<void> {
    let version: string | undefined;
    try {
      // no-store: never let a cached response hide a real version change (and /api is exempt from the
      // no-cache filter, so the browser could otherwise heuristically cache it).
      const res = await fetch('/api/platform', { cache: 'no-store' });
      version = (await res.json())?.version;
    } catch {
      return; // transient (backend still coming back up) — re-checked on the next reconnect
    }
    if (!version) return;

    if (this.baseline === null) {
      this.baseline = version; // first connect ≈ page load: this is the version we're running
    } else if (version !== this.baseline) {
      location.reload();
    }
  }
}
