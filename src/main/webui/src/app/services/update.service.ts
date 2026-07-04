import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ToastService } from '../ui/toast/toast.service';

interface UpdateTarget { version: string; installerUrl: string; }
interface UpdateError { error: string; }

/**
 * Windows self-update (installed builds only — see {@link PlatformService.autoUpdate}). Asks the backend
 * to download the release installer and run it silently; the app then closes and relaunches itself, so
 * the websocket drops as confirmation. On other platforms the UI links to the download page instead and
 * never calls this.
 */
@Injectable({ providedIn: 'root' })
export class UpdateService {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);

  /** Update to the latest available release. */
  updateToLatest(): void {
    this.run('/api/system/update', 'Downloading the update…');
  }

  /** Debug: re-download and reinstall the currently running version, to test the update flow. */
  reinstallCurrent(): void {
    this.run('/api/system/update/reinstall', 'Re-downloading the current installer…');
  }

  private async run(url: string, pending: string): Promise<void> {
    const id = this.toast.show(pending, { kind: 'info', timeout: 0 });
    try {
      const target = await firstValueFrom(this.http.post<UpdateTarget>(url, {}));
      this.toast.dismiss(id);
      this.toast.show('Update started', {
        sub: `Installing ${target.version}. The app will close and restart.`,
        kind: 'success',
        timeout: 0,
      });
    } catch (e: unknown) {
      this.toast.dismiss(id);
      const msg = (e as { error?: UpdateError })?.error?.error ?? 'The update could not be started.';
      this.toast.show('Update failed', { sub: msg, kind: 'error', timeout: 6000 });
    }
  }
}
