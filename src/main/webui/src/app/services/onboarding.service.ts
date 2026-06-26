import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { OnboardingDto } from '../models/generated/backend.types';

/**
 * Fetches the one-time startup onboarding hint (new-user vs post-install dialog) from the backend and
 * acknowledges it so it doesn't reappear on refresh.
 */
@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);
  readonly info = signal<OnboardingDto | null>(null);

  load(): void {
    this.http.get<OnboardingDto>('/api/system/onboarding').subscribe({
      next: (i) => this.info.set(i),
      error: () => this.info.set(null),
    });
  }

  ack(): void {
    this.http.post('/api/system/onboarding/ack', {}).subscribe({ error: () => {} });
  }
}
