import { computed, inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, httpResource } from '@angular/common/http';
import { LightingTraceRequestDto, LightingTraceStateDto } from '../models/generated/backend.types';
import { ToastService } from '../ui/toast/toast.service';

/**
 * The lighting trace (Settings > Debug): a record of every report the app sends to a PCPanel, kept by the
 * backend in a log file of its own. The switch survives restarts; the mark adds a line the moment the user
 * sees the lights go out, so the trace can be read around it.
 */
@Injectable({ providedIn: 'root' })
export class LightingTraceService {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  private readonly state = httpResource<LightingTraceStateDto>(() => '/api/lighting-trace');

  readonly enabled = computed(() => this.state.value()?.enabled ?? false);
  readonly file = computed(() => this.state.value()?.file ?? '');

  set(enabled: boolean): void {
    const body: LightingTraceRequestDto = { enabled };
    this.http.put<LightingTraceStateDto>('/api/lighting-trace', body).subscribe({
      next: () => this.state.reload(),
      error: (e: HttpErrorResponse) => {
        this.state.reload();
        this.toast.show('The lighting trace could not be switched', { sub: e.error?.error ?? e.message, kind: 'error' });
      },
    });
  }

  markLightsOut(): void {
    this.http.post('/api/lighting-trace/mark', null).subscribe({
      next: () => this.toast.show('Marked in the lighting trace', { kind: 'success' }),
      error: (e: HttpErrorResponse) => this.toast.show('The mark could not be added', { sub: e.error?.error ?? e.message, kind: 'error' }),
    });
  }
}
