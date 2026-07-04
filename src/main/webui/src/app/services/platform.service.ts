import { computed, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';

interface PlatformInfo { os: string; voicemeeter: boolean; waveLink: boolean; flatpak: boolean; autoUpdate: boolean; version: string; branch?: string | null; commit?: string | null; }

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
  /** Running app version reported by the backend (e.g. "2.0-SNAPSHOT"); avoids hardcoding it. */
  readonly version = computed(() => this.info.value()?.version ?? '');
  /** Git branch of a local (SNAPSHOT) build, so several dev instances can be told apart; '' for releases. */
  readonly branch = computed(() => this.info.value()?.branch ?? '');
  /** Short HEAD commit of a local build, so the exact running build is identifiable; '' for releases. */
  readonly commit = computed(() => this.info.value()?.commit ?? '');
  /** Default true until the backend answers, so supported integrations aren't hidden on load. */
  readonly voicemeeterSupported = computed(() => this.info.value()?.voicemeeter ?? true);
  readonly waveLinkSupported = computed(() => this.info.value()?.waveLink ?? true);
  /** True on an installed Windows build, where the app can download + run the installer itself; else the UI links to the release download page. Default false until the backend answers. */
  readonly autoUpdate = computed(() => this.info.value()?.autoUpdate ?? false);
  /** True only inside the Flatpak sandbox, where Discord's IPC socket is visible only if Discord was already running at launch. Default false until the backend answers. */
  readonly flatpak = computed(() => this.info.value()?.flatpak ?? false);
}
