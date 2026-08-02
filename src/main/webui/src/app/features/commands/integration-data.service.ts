import { computed, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { AudioDevice, AudioSession } from '../../models/models';
import {
  DiscordStatusDto, DiscordUserDto, DiscordVoiceChannelDto, HomeAssistantServerStatus, ProcessDto, WaveLinkResponseDto,
} from '../../models/generated/backend.types';
import { PickerItem } from '../../ui';

export interface VoiceMeeterParams { name: string; params: string[]; }

/** The name the backend matches for the Windows System Sounds audio session (AudioSession.SYSTEM). */
const SYSTEM_SOUNDS = 'System Sounds';

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

  // OSC / MQTT live status (the backend only reports a positive state when actually connected/listening)
  readonly oscStatus = httpResource<{ enabled: boolean; listening: boolean }>(() => '/api/osc/status');
  readonly mqttStatus = httpResource<{ connected: boolean }>(() => '/api/settings/mqtt/status');

  // Wave Link
  readonly waveLink = httpResource<WaveLinkResponseDto>(() => '/api/wavelink/devices');
  readonly waveLinkSettings = httpResource<{ enabled: boolean }>(() => '/api/settings/wavelink');
  readonly wlChannels = computed(() => this.waveLink.value()?.channels ?? []);
  readonly wlInputs = computed(() => this.waveLink.value()?.inputs ?? []);
  readonly wlMixes = computed(() => this.waveLink.value()?.mixes ?? []);
  readonly wlOutputs = computed(() => this.waveLink.value()?.outputs ?? []);

  // Discord (users = current voice-channel members + persisted "seen users" roster)
  readonly discordUsers = httpResource<DiscordUserDto[]>(() => '/api/discord/users');
  readonly discordChannels = httpResource<DiscordVoiceChannelDto[]>(() => '/api/discord/voice-channels');
  readonly discordStatus = httpResource<DiscordStatusDto>(() => '/api/discord/status');
  /** Green only when authenticated — that's when the voice commands actually work. */
  readonly discordConnected = computed(() => this.discordStatus.value()?.authenticated ?? false);

  // Home Assistant (servers carry their url so the action editor can link to HA's action builder)
  readonly haServers = httpResource<HomeAssistantServerStatus[]>(() => '/api/homeassistant/servers');
  readonly haStatus = httpResource<{ connected: boolean }>(() => '/api/homeassistant/status');
  readonly haConnected = computed(() => this.haStatus.value()?.connected ?? false);

  // ── Honest connection state (frontend-only) ─────────────────────────────────
  // OBS returns data ONLY while it is actually connected (empty otherwise), so
  // non-empty data is a reliable "connected" signal for it. Integrations whose
  // data outlives the connection report their state themselves, as a field on
  // the response. Voicemeeter's REST endpoint is a stub that
  // always returns [], so there is no live signal — we never claim it connected.
  readonly obsConnected = computed(() =>
    (this.obsScenes.value()?.length ?? 0) > 0 || (this.obsSources.value()?.length ?? 0) > 0);
  /**
   * Wave Link reports its own connection state: its lists outlive a disconnect (they keep naming the
   * targets a command is configured against), so a populated list is no evidence the connection is up.
   */
  readonly waveLinkConnected = computed(() => this.waveLink.value()?.connected ?? false);
  /** No live Voicemeeter signal exists (REST stub) — connection state is unknown. */
  readonly voicemeeterConnected = computed(() => false);
  /** OSC is "connected" when its listen socket is actually bound. */
  readonly oscListening = computed(() => this.oscStatus.value()?.listening ?? false);
  /** MQTT broker connection state (Paho client). */
  readonly mqttConnected = computed(() => this.mqttStatus.value()?.connected ?? false);

  /** Process list as picker items (deduped, with real icons when present). System Sounds is an audio
   *  session rather than a process, so /api/processes never lists it — it is pinned on top explicitly
   *  so app-volume / app-mute can target it (the backend matches the "System Sounds" name). */
  readonly processItems = computed<PickerItem[]>(() => {
    const seen = new Set<string>();
    const out: PickerItem[] = [];
    for (const p of this.processes.value() ?? []) {
      if (!p.name || seen.has(p.name)) continue;
      seen.add(p.name);
      out.push({ key: p.name, label: p.name, icon: p.icon ?? null });
    }
    out.sort((a, b) => a.label.localeCompare(b.label));
    const sysIcon = (this.sessions.value() ?? []).find(s => s.title === SYSTEM_SOUNDS)?.icon ?? null;
    out.unshift({ key: SYSTEM_SOUNDS, label: SYSTEM_SOUNDS, icon: sysIcon });
    return out;
  });

  /** Audio devices as picker items, tagged OUTPUT/INPUT. */
  readonly deviceItems = computed<PickerItem[]>(() =>
    (this.audioDevices.value() ?? []).map(d => ({
      key: d.id, label: d.name, sub: d.output ? 'OUTPUT' : d.input ? 'INPUT' : undefined,
    })));

  /** Wall-clock time of the last process-list fetch, for {@link refreshProcesses}. */
  private lastProcessRefresh = 0;

  /**
   * Re-reads the running-application list, for callers that are about to show it.
   *
   * httpResource fetches once and then caches for the lifetime of this service — and the service is
   * `providedIn: 'root'`, so that lifetime is the whole page. Nothing else re-fetched it, which is why the
   * app picker showed the list as it was when the page was opened: start a program, open "Add app", and it
   * isn't there. Closing and reopening PCPanel appeared to be the fix only because that reopens the UI, and
   * a fresh page re-fetches (#151).
   *
   * Throttled, because opening a picker is a click and the list is a subprocess call on the backend.
   */
  refreshProcesses(): void {
    const now = Date.now();
    if (now - this.lastProcessRefresh < 1000) return;
    this.lastProcessRefresh = now;
    this.processes.reload();
  }

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
    this.discordUsers.reload();
    this.discordStatus.reload();
    this.oscStatus.reload();
    this.mqttStatus.reload();
    this.haServers.reload();
    this.haStatus.reload();
  }
}
