import { computed, inject, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { AudioDevice, AudioSession } from '../../models/models';
import { ProcessDto, WaveLinkResponseDto } from '../../models/generated/backend.types';
import { PickerItem } from '../../ui';

export interface VoiceMeeterParams { name: string; params: string[]; }

/**
 * Live data sources for the command editor's dropdowns and pickers. Wraps the
 * backend integration endpoints as reactive httpResources so the action forms
 * can populate themselves without manual subscription management.
 */
@Injectable({ providedIn: 'root' })
export class IntegrationDataService {
  // Audio
  readonly audioDevices = httpResource<AudioDevice[]>(() => '/api/audio/devices');
  readonly outputDevices = httpResource<AudioDevice[]>(() => '/api/audio/devices/output');
  readonly inputDevices = httpResource<AudioDevice[]>(() => '/api/audio/devices/input');
  readonly sessions = httpResource<AudioSession[]>(() => '/api/audio/sessions');
  readonly processes = httpResource<ProcessDto[]>(() => '/api/processes');

  // OBS
  readonly obsScenes = httpResource<string[]>(() => '/api/obs/scenes');
  readonly obsSources = httpResource<string[]>(() => '/api/obs/sources');

  // Voicemeeter
  readonly vmBasic = httpResource<VoiceMeeterParams[]>(() => '/api/voicemeeter/basic');
  readonly vmAdvanced = httpResource<VoiceMeeterParams[]>(() => '/api/voicemeeter/advanced');

  // Wave Link
  readonly waveLink = httpResource<WaveLinkResponseDto>(() => '/api/wavelink/devices');
  readonly waveLinkSettings = httpResource<{ enabled: boolean }>(() => '/api/settings/wavelink');
  readonly wlChannels = computed(() => this.waveLink.value()?.channels ?? []);
  readonly wlInputs = computed(() => this.waveLink.value()?.inputs ?? []);
  readonly wlMixes = computed(() => this.waveLink.value()?.mixes ?? []);
  readonly wlOutputs = computed(() => this.waveLink.value()?.outputs ?? []);

  // ── Honest connection state (frontend-only) ─────────────────────────────────
  // The backend endpoints return data ONLY when the integration is actually
  // connected (empty otherwise), so non-empty data is a reliable "connected"
  // signal for OBS and Wave Link. Voicemeeter's REST endpoint is a stub that
  // always returns [], so there is no live signal — we never claim it connected.
  readonly obsConnected = computed(() =>
    (this.obsScenes.value()?.length ?? 0) > 0 || (this.obsSources.value()?.length ?? 0) > 0);
  readonly waveLinkConnected = computed(() =>
    this.wlChannels().length > 0 || this.wlInputs().length > 0 || this.wlMixes().length > 0 || this.wlOutputs().length > 0);
  /** No live Voicemeeter signal exists (REST stub) — connection state is unknown. */
  readonly voicemeeterConnected = computed(() => false);

  /** Process list as picker items (deduped, with real icons when present). */
  readonly processItems = computed<PickerItem[]>(() => {
    const seen = new Set<string>();
    const out: PickerItem[] = [];
    for (const p of this.processes.value() ?? []) {
      if (!p.name || seen.has(p.name)) continue;
      seen.add(p.name);
      out.push({ key: p.name, label: p.name, icon: p.icon ?? null });
    }
    return out.sort((a, b) => a.label.localeCompare(b.label));
  });

  /** Audio devices as picker items, tagged OUTPUT/INPUT. */
  readonly deviceItems = computed<PickerItem[]>(() =>
    (this.audioDevices.value() ?? []).map(d => ({
      key: d.id, label: d.name, sub: d.output ? 'OUTPUT' : d.input ? 'INPUT' : undefined,
    })));

  /** Reload everything (call after a save that may change the lists). */
  reload(): void {
    this.audioDevices.reload();
    this.outputDevices.reload();
    this.inputDevices.reload();
    this.sessions.reload();
    this.processes.reload();
    this.obsScenes.reload();
    this.obsSources.reload();
    this.vmBasic.reload();
    this.vmAdvanced.reload();
    this.waveLink.reload();
    this.waveLinkSettings.reload();
  }
}
