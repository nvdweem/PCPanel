import { computed, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';

interface PlatformInfo { os: string; voicemeeter: boolean; waveLink: boolean; }

/**
 * Platform capabilities reported by the BACKEND (the machine the device + native
 * integrations actually run on) — not the browser, since the UI can be opened
 * from another device on the network. Drives hiding of platform-specific
 * integrations (Voicemeeter = Windows only; Wave Link = Windows/macOS only).
 */
@Injectable({ providedIn: 'root' })
export class PlatformService {
  private readonly info = httpResource<PlatformInfo>(() => '/api/platform');

  readonly os = computed(() => this.info.value()?.os ?? '');
  /** Default true until the backend answers, so supported integrations aren't hidden on load. */
  readonly voicemeeterSupported = computed(() => this.info.value()?.voicemeeter ?? true);
  readonly waveLinkSupported = computed(() => this.info.value()?.waveLink ?? true);
}
