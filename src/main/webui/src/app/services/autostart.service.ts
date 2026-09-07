import { computed, inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, httpResource } from '@angular/common/http';
import { AutostartRequestDto, AutostartStateDto } from '../models/generated/backend.types';
import { ToastService } from '../ui/toast/toast.service';

/**
 * The "Start with Windows" registration, read from and written to the backend (the registry is the
 * single source of truth; nothing is kept in the settings file). Only an installed Windows build reports
 * it {@link supported}; the UI renders the switch nowhere else. When the installer's "run as
 * administrator" scheduled task exists the switch shows on but is read-only, since that registration is
 * the installer's to change.
 */
@Injectable({ providedIn: 'root' })
export class AutostartService {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly state = httpResource<AutostartStateDto>(() => '/api/platform/autostart');

  /** False until the backend answers, so the switch never flashes on a platform that has none. */
  readonly supported = computed(() => this.state.value()?.supported ?? false);
  readonly enabled = computed(() => this.state.value()?.enabled ?? false);
  readonly elevatedTask = computed(() => this.state.value()?.elevatedTask ?? false);

  set(enabled: boolean): void {
    const body: AutostartRequestDto = { enabled };
    this.http.put<AutostartStateDto>('/api/platform/autostart', body).subscribe({
      next: () => this.state.reload(),
      error: (e: HttpErrorResponse) => {
        this.state.reload();
        this.toast.show('Start with Windows could not be changed', { sub: e.error?.error ?? e.message, kind: 'error' });
      },
    });
  }
}
