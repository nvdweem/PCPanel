import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, HostListener, inject, linkedSignal, signal, untracked } from '@angular/core';
import { HistoryButtonsComponent } from '../../features/history/history-buttons.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, httpResource } from '@angular/common/http';
import { SettingsService } from '../../services/settings.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { PlatformService } from '../../services/platform.service';
import { DebugService, DeviceTypeOverride, OsOverride } from '../../services/debug.service';
import { UpdateService } from '../../services/update.service';
import { AutostartService } from '../../services/autostart.service';
import { DeviceStateService } from '../../services/device-state.service';
import { HistoryService } from '../../services/history.service';
import {
  AlertEffect, CurveDefinition, DiscordSettings, NotificationAlert, SaveBackup, DiscordStatusDto, FocusVolumeOverride, FocusVolumeTarget, OverlayPosition, SettingsDto, SonarSettings,
  PlatformLimitsDto, WaveLinkSettings,
} from '../../models/generated/backend.types';
import {
  AppPickerComponent,
  ColorPickerComponent, IconComponent, IconName, ModalComponent, SegmentedComponent, SegmentOption,
  SelectComponent, SelectOption, SliderComponent, SpinnerComponent, StatusDotComponent, StatusKind, ToastService, ToggleComponent,
} from '../../ui';
import { CommandPickerComponent } from '../../features/commands/command-picker.component';
import { CommandFieldsComponent } from '../../features/commands/command-fields.component';
import { COMMAND_BY_TYPE, CommandDef } from '../../features/commands/command-catalog';
import { CurveEditorComponent } from '../../features/curves/curve-editor.component';
import { CurveGraphComponent } from '../../features/curves/curve-graph.component';
import { BUILT_IN_DEFAULTS, isBuiltIn } from '../../features/curves/curve.util';
import { OverlayPreviewRenderer, overlayPreviewStyle } from './overlay-preview';
import { PanelTestComponent } from '../../features/panel-test/panel-test.component';

type Cmd = Record<string, any>;
/** Mirror SonarSettings' DEFAULT/MIN/MAX_UPDATES_PER_SECOND (6–25); the backend clamps as well. */
const SONAR_DEFAULT_RATE = 12;
const SONAR_MIN_RATE = 6;
const SONAR_MAX_RATE = 25;
/** Above this rate the Update rate row warns about SteelSeries GG's CPU use. */
const SONAR_HIGH_CPU_RATE = 20;

type TabId = 'general' | 'alerts' | 'curves' | 'focusoverride' | 'obs' | 'voicemeeter' | 'wavelink' | 'discord' | 'osc' | 'mqtt' | 'homeassistant' | 'sonar' | 'overlay' | 'debug';
interface TabDef { id: TabId; label: string; integration?: 'obs' | 'voicemeeter' | 'wavelink'; supported?: boolean; }

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [HistoryButtonsComponent, 
    IconComponent, StatusDotComponent, SpinnerComponent, ToggleComponent,
    SegmentedComponent, SliderComponent, ColorPickerComponent, ModalComponent, SelectComponent,
    OverlayModule, AppPickerComponent, CommandPickerComponent, CommandFieldsComponent,
    CurveEditorComponent, CurveGraphComponent, PanelTestComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
  private readonly settingsService = inject(SettingsService);
  private readonly integrations = inject(IntegrationDataService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  readonly platform = inject(PlatformService);
  readonly debug = inject(DebugService);
  readonly updates = inject(UpdateService);
  readonly autostart = inject(AutostartService);
  readonly state = inject(DeviceStateService);
  private readonly history = inject(HistoryService);

  readonly deviceOverrideOptions: SelectOption<DeviceTypeOverride>[] = [
    { value: '', label: 'Off — show real device' },
    { value: 'PCPANEL_PRO', label: 'PCPanel Pro' },
    { value: 'PCPANEL_RGB', label: 'PCPanel RGB' },
    { value: 'PCPANEL_MINI', label: 'PCPanel Mini' },
  ];

  readonly osOverrideOptions: SelectOption<OsOverride>[] = [
    { value: '', label: 'Real OS' },
    { value: 'windows', label: 'Windows' },
    { value: 'mac', label: 'macOS' },
    { value: 'linux', label: 'Linux' },
  ];

  readonly settings = this.settingsService.settings;

  /**
   * Placeholder the backend returns in place of a stored secret (obs/mqtt passwords, mqtt username,
   * Discord client secret, Home Assistant tokens) — must match {@code SecretMasking.MASK} on the Java
   * side. The real value is never sent to the browser; a field holding this is "configured". A save
   * that echoes it back (or sends blank) keeps the stored secret, so the user only ever types a value
   * to *change* it.
   */
  private readonly SECRET_MASK = '••••••••';

  /** Value to show in a secret input: blank when it's the mask (so the user types over an empty field). */
  secretValue(v: string | null | undefined): string {
    return v === this.SECRET_MASK ? '' : (v ?? '');
  }

  /** True when the field currently holds the mask, i.e. a secret is stored and hasn't been re-typed. */
  secretConfigured(v: string | null | undefined): boolean {
    return v === this.SECRET_MASK;
  }

  /** Placeholder for a secret input: a "configured" hint when a value is stored, else the given default. */
  secretPlaceholder(v: string | null | undefined, fallback = ''): string {
    return this.secretConfigured(v) ? 'Saved — leave blank to keep' : fallback;
  }

  /** Replace any still-masked secret with '' so we never round-trip the mask as if it were a new value
   *  (the backend treats blank as "keep the stored secret", identical to sending the mask). */
  private stripSecretMask(v: string | null | undefined): string {
    return v === this.SECRET_MASK ? '' : (v ?? '');
  }

  readonly activeTab = signal<TabId>('general');
  readonly saving = signal(false);
  readonly confirmLeaveOpen = signal(false);

  readonly quitConfirmOpen = signal(false);
  /** Drives the Quit button and the post-shutdown overlay: idle → quitting → stopped. */
  readonly quitState = signal<'idle' | 'quitting' | 'stopped'>('idle');

  /** Local editable copy, seeded once from the resource (or when not dirty). */
  readonly local = signal<SettingsDto | null>(null);
  readonly dirty = signal(false);

  /**
   * Backend-rendered overlay previews (the real renderer → PNG): the saved settings next to the local,
   * possibly unsaved, edits so a style change can be compared against what is currently in effect.
   */
  readonly savedOverlayPreview = new OverlayPreviewRenderer(this.http);
  readonly editedOverlayPreview = new OverlayPreviewRenderer(this.http);

  // OSC add-form fields
  readonly oscHost = signal('');
  readonly oscPort = signal('');

  // Home Assistant add-form fields
  readonly haName = signal('');
  readonly haUrl = signal('');
  readonly haToken = signal('');

  private readonly allTabs: TabDef[] = [
    { id: 'general', label: 'General' },
    { id: 'alerts', label: 'Notification lights' },
    { id: 'curves', label: 'Curves' },
    { id: 'focusoverride', label: 'Focus Override' },
    { id: 'overlay', label: 'Overlay' },
    { id: 'obs', label: 'OBS Studio', integration: 'obs' },
    { id: 'voicemeeter', label: 'Voicemeeter', integration: 'voicemeeter' },
    { id: 'wavelink', label: 'Wave Link', integration: 'wavelink' },
    { id: 'discord', label: 'Discord' },
    { id: 'osc', label: 'OSC' },
    { id: 'mqtt', label: 'MQTT' },
    { id: 'homeassistant', label: 'Home Assistant' },
    { id: 'sonar', label: 'SteelSeries Sonar' },
    { id: 'debug', label: 'Debug' },
  ];

  /** All tabs shown; platform-specific ones flagged unsupported (shown disabled). */
  readonly tabs = computed<TabDef[]>(() => this.allTabs.map(t => ({ ...t, supported: this.tabSupported(t.id) })));

  tabSupported(id: TabId): boolean {
    if (id === 'voicemeeter') return this.platform.voicemeeterSupported();
    if (id === 'wavelink') return this.platform.waveLinkSupported();
    if (id === 'sonar') return this.platform.sonarSupported();
    return true;
  }

  platformNote(id: TabId): string {
    if (id === 'voicemeeter') return 'Voicemeeter is only available on Windows.';
    if (id === 'wavelink') return 'Elgato Wave Link is only available on Windows and macOS.';
    if (id === 'sonar') return 'SteelSeries Sonar is only available on Windows.';
    return '';
  }

  /**
   * On Linux the overlay is drawn as a system notification (the notification daemon owns its
   * appearance and placement), so the position/size/colour settings have no effect there and are
   * shown disabled. Enable / Show number / Scale still apply. Defaults to supported until the
   * backend platform answers, so the controls aren't briefly greyed out on load.
   */
  readonly overlayStylingSupported = computed(() => this.platform.os() !== 'linux');

  readonly scaleOptions: SegmentOption<boolean>[] = [
    { value: true, label: 'Log' },
    { value: false, label: 'Linear' },
  ];

  // Position grid in row-major order (top → bottom, left → right).
  readonly gridPositions: OverlayPosition[] = [
    'topLeft', 'topMiddle', 'topRight',
    'middleLeft', 'middleMiddle', 'middleRight',
    'bottomLeft', 'bottomMiddle', 'bottomRight',
  ];

  // Wave Link settings (not part of SettingsDto): enable + focus-control + controlled-volume options.
  readonly wavelinkSettings = httpResource<WaveLinkSettings>(() => '/api/settings/wavelink');

  // SteelSeries Sonar settings (not part of SettingsDto): an enable switch and the update rate. Live connection
  // status comes from the shared integrations status endpoint, same as OBS/Wave Link/Discord.
  readonly sonarSettings = httpResource<SonarSettings>(() => '/api/settings/sonar');
  /** The rate the slider shows: follows the saved value, and the drag in progress until it is released. */
  readonly sonarRate = linkedSignal(() => this.sonarSettings.value()?.updatesPerSecond ?? SONAR_DEFAULT_RATE);
  /** Follows the drag in progress, so the warning shows as soon as the slider passes the threshold. */
  readonly sonarRateHigh = computed(() => this.sonarRate() > SONAR_HIGH_CPU_RATE);
  readonly sonarMinRate = SONAR_MIN_RATE;
  readonly sonarMaxRate = SONAR_MAX_RATE;
  readonly sonarTabStatus = computed<StatusKind>(() =>
    this.integrations.sonarConnected() ? 'ok' : this.integrations.sonarLoading() ? 'connecting' : 'idle');
  // Exposed for the template (the injected integrations service is private).
  readonly sonarStatusValue = computed(() => this.integrations.sonarStatus.value());

  // Discord settings (not part of SettingsDto): enable + client id/secret. Edited in a local draft and
  // saved explicitly (credentials shouldn't PUT on every keystroke); status comes from the live endpoint.
  readonly discordSettings = httpResource<DiscordSettings>(() => '/api/settings/discord');
  readonly discordDraft = signal<DiscordSettings | null>(null);
  readonly discordDirty = signal(false);
  readonly discordAuthorizing = signal(false);
  readonly discordTabStatus = computed<StatusKind>(() =>
    this.integrations.discordConnected() ? 'ok' : this.integrations.discordStatus.isLoading() ? 'connecting' : 'idle');
  // Exposed for the template (the injected integrations service is private).
  readonly discordStatusValue = computed(() => this.integrations.discordStatus.value());
  readonly discordUsersValue = computed(() => this.integrations.discordUsers.value() ?? []);
  readonly discordConnOpen = signal(false); // expander for the client id/secret/uri once connected

  // Font families the backend (Java2D) can actually render — the overlay font picker only offers these.
  readonly overlayFonts = httpResource<string[]>(() => '/api/overlay/fonts');
  readonly fontOptions = computed<SelectOption<string>[]>(() => {
    const fonts = this.overlayFonts.value() ?? [];
    // Render each option's label in its own family so the picker previews the font (the local browser
    // has the same system fonts the backend enumerates).
    return [{ value: '', label: 'Default (Segoe UI)', font: 'Segoe UI' }, ...fonts.map(f => ({ value: f, label: f, font: f }))];
  });

  readonly savedOverlayPreviewStyle = computed(() => overlayPreviewStyle(this.settings.value()));
  readonly editedOverlayPreviewStyle = computed(() => overlayPreviewStyle(this.local()));

  constructor() {
    // Seed the editable copy the first time real settings arrive, or whenever the
    // server snapshot changes while we are not mid-edit (e.g. after a reload).
    effect(() => {
      const v = this.settings.value();
      if (!v) return;
      untracked(() => {
        if (this.local() && this.dirty()) return;
        this.local.set(structuredClone(v));
      });
    });

    // An undo or redo replaced the configuration: show it, unless there are unsaved edits here.
    effect(() => {
      if (!this.history.applied()) return;
      untracked(() => {
        if (!this.dirty()) this.settings.reload();
        this.backups.reload();
      });
    });

    // Deep-link support: /settings?tab=homeassistant (used by the action editor's "Manage servers").
    const tab = this.route.snapshot.queryParamMap.get('tab') as TabId | null;
    if (tab && this.allTabs.some(t => t.id === tab)) this.activeTab.set(tab);

    // Seed the Discord credential draft from the server, unless the user is mid-edit.
    effect(() => {
      const v = this.discordSettings.value();
      if (!v) return;
      untracked(() => {
        if (!this.discordDirty()) this.discordDraft.set({ ...v });
      });
    });

    // Live overlay previews: render the real overlay on the backend, debounced so dragging a slider
    // doesn't fire a request per tick. Guarantees the previews can't drift from what the on-screen
    // overlay actually draws. The saved pane only re-renders when the server snapshot changes.
    const overlayPreviewActive = computed(() => this.activeTab() === 'overlay' && this.overlayStylingSupported());
    effect(() => {
      const s = this.settings.value();
      if (s && overlayPreviewActive()) untracked(() => this.savedOverlayPreview.schedule(s));
    });
    effect(() => {
      const s = this.local();
      if (s && overlayPreviewActive()) untracked(() => this.editedOverlayPreview.schedule(s));
    });
    // The backend finds Sonar on a one-second poll, so while its tab is open the status is re-read every
    // couple of seconds: switching Sonar on, or starting and stopping GG, shows up without leaving the tab.
    let sonarTimer: ReturnType<typeof setInterval> | undefined;
    const stopSonarRefresh = () => {
      if (sonarTimer) {
        clearInterval(sonarTimer);
        sonarTimer = undefined;
      }
    };
    effect(() => {
      if (this.activeTab() === 'sonar' && this.platform.sonarSupported()) {
        untracked(() => {
          this.integrations.refreshSonarStatus();
          sonarTimer ??= setInterval(() => this.integrations.refreshSonarStatus(), 2000);
        });
      } else {
        stopSonarRefresh();
      }
    });
    // Notifications are only followed once something asks, so whether that works is known a moment after the
    // notification-lights page first asks: look again shortly after opening it.
    let limitsTimer: ReturnType<typeof setTimeout> | undefined;
    effect(() => {
      if (this.activeTab() === 'alerts') {
        untracked(() => {
          this.limits.reload();
          clearTimeout(limitsTimer);
          limitsTimer = setTimeout(() => this.limits.reload(), 3000);
        });
      }
    });
    inject(DestroyRef).onDestroy(() => {
      this.savedOverlayPreview.dispose();
      this.editedOverlayPreview.dispose();
      stopSonarRefresh();
      clearTimeout(limitsTimer);
    });
  }

  /** What this desktop can't detect (screens off, locking, notifications), shown next to the options that rely on it. */
  readonly limits = httpResource<PlatformLimitsDto>(() => '/api/platform/limits');

  /** Browser refresh / tab close: warn if there are unsaved edits (in-app nav is guarded by back()). */
  @HostListener('window:beforeunload', ['$event'])
  onBeforeUnload(e: BeforeUnloadEvent): void {
    if (this.dirty()) {
      e.preventDefault();
      e.returnValue = '';
    }
  }

  // ── status dots for integration tabs ───────────────────────────────────────
  tabStatus(integration: 'obs' | 'voicemeeter' | 'wavelink' | undefined): StatusKind | null {
    if (!integration) return null;
    // Honest state: green only with positive evidence of a live connection.
    if (integration === 'obs') {
      return this.integrations.obsConnected() ? 'ok' : this.integrations.obsScenes.isLoading() ? 'connecting' : 'idle';
    }
    if (integration === 'wavelink') {
      return this.integrations.waveLinkConnected() ? 'ok' : this.integrations.waveLink.isLoading() ? 'connecting' : 'idle';
    }
    return 'idle'; // voicemeeter: no live REST signal
  }

  /** OSC tab dot: green when the listener is bound, else neutral (idle/connecting), like OBS. */
  readonly oscTabStatus = computed<StatusKind>(() =>
    this.integrations.oscListening() ? 'ok' : this.integrations.oscStatus.isLoading() ? 'connecting' : 'idle');

  /** MQTT tab dot: green when the broker is connected, else neutral, like OBS. */
  readonly mqttTabStatus = computed<StatusKind>(() =>
    this.integrations.mqttConnected() ? 'ok' : this.integrations.mqttStatus.isLoading() ? 'connecting' : 'idle');

  /** Home Assistant tab dot: green when any configured server is reachable. */
  readonly haTabStatus = computed<StatusKind>(() =>
    this.integrations.haConnected() ? 'ok' : this.integrations.haStatus.isLoading() ? 'connecting' : 'idle');

  /** Status dot shown in the left tab rail for every integration tab (null = no dot). */
  railStatus(id: TabId): StatusKind | null {
    switch (id) {
      case 'obs': return this.tabStatus('obs');
      case 'voicemeeter': return this.platform.voicemeeterSupported() ? this.tabStatus('voicemeeter') : 'disabled';
      case 'wavelink': return this.platform.waveLinkSupported() ? this.tabStatus('wavelink') : 'disabled';
      case 'discord': return this.discordTabStatus();
      case 'osc': return this.oscTabStatus();
      case 'mqtt': return this.mqttTabStatus();
      case 'homeassistant': return this.haTabStatus();
      case 'sonar': return this.platform.sonarSupported() ? this.sonarTabStatus() : 'disabled';
      default: return null;
    }
  }

  setTab(id: TabId): void { this.activeTab.set(id); }

  // ── generic field updates (immutable) ───────────────────────────────────────
  patch<K extends keyof SettingsDto>(key: K, value: SettingsDto[K]): void {
    const cur = this.local();
    if (!cur) return;
    this.local.set({ ...cur, [key]: value });
    this.dirty.set(true);
  }

  /** Coerce a raw input string to a number, falling back to a default. */
  num(raw: string, fallback = 0): number {
    const n = Number(raw);
    return Number.isFinite(n) ? n : fallback;
  }

  patchNum<K extends keyof SettingsDto>(key: K, raw: string, fallback = 0): void {
    this.patch(key, this.num(raw, fallback) as SettingsDto[K]);
  }

  // ── 'send only on change' maps onto the numeric sendOnlyIfDelta field ───────
  readonly sendOnlyOnChange = computed(() => (this.local()?.sendOnlyIfDelta ?? 0) > 0);
  setSendOnlyOnChange(on: boolean): void { this.patch('sendOnlyIfDelta', (on ? 1 : 0)); }

  // ── nested MQTT updates ─────────────────────────────────────────────────────
  patchMqtt<K extends keyof SettingsDto['mqtt']>(key: K, value: SettingsDto['mqtt'][K]): void {
    const cur = this.local();
    if (!cur) return;
    this.local.set({ ...cur, mqtt: { ...cur.mqtt, [key]: value } });
    this.dirty.set(true);
  }

  patchMqttNum<K extends keyof SettingsDto['mqtt']>(key: K, raw: string, fallback = 0): void {
    this.patchMqtt(key, this.num(raw, fallback) as SettingsDto['mqtt'][K]);
  }

  patchHa<K extends keyof SettingsDto['mqtt']['homeAssistant']>(
    key: K, value: SettingsDto['mqtt']['homeAssistant'][K],
  ): void {
    const cur = this.local();
    if (!cur) return;
    this.local.set({
      ...cur,
      mqtt: { ...cur.mqtt, homeAssistant: { ...cur.mqtt.homeAssistant, [key]: value } },
    });
    this.dirty.set(true);
  }

  // ── OSC send targets ────────────────────────────────────────────────────────
  addOscTarget(): void {
    const cur = this.local();
    if (!cur) return;
    const host = this.oscHost().trim();
    const port = this.num(this.oscPort());
    if (!host || port <= 0) return;
    this.local.set({ ...cur, oscConnections: [...(cur.oscConnections ?? []), { host, port }] });
    this.dirty.set(true);
    this.oscHost.set('');
    this.oscPort.set('');
  }

  removeOscTarget(i: number): void {
    const cur = this.local();
    if (!cur) return;
    this.local.set({ ...cur, oscConnections: (cur.oscConnections ?? []).filter((_, k) => k !== i) });
    this.dirty.set(true);
  }

  // ── Home Assistant servers ───────────────────────────────────────────────────
  private newServerId(): string {
    const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
    return c?.randomUUID ? c.randomUUID() : `ha-${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
  }

  addHaServer(): void {
    const cur = this.local();
    if (!cur) return;
    const name = this.haName().trim();
    const url = this.haUrl().trim();
    if (!name || !url) return;
    const server = { id: this.newServerId(), name, url, token: this.haToken().trim() };
    this.local.set({ ...cur, homeAssistantServers: [...(cur.homeAssistantServers ?? []), server] });
    this.dirty.set(true);
    this.haName.set('');
    this.haUrl.set('');
    this.haToken.set('');
  }

  removeHaServer(i: number): void {
    const cur = this.local();
    if (!cur) return;
    this.local.set({ ...cur, homeAssistantServers: (cur.homeAssistantServers ?? []).filter((_, k) => k !== i) });
    this.dirty.set(true);
  }

  patchHaServer(i: number, key: 'name' | 'url' | 'token', value: string): void {
    const cur = this.local();
    if (!cur) return;
    const list = (cur.homeAssistantServers ?? []).map((s, k) => (k === i ? { ...s, [key]: value } : s));
    this.local.set({ ...cur, homeAssistantServers: list });
    this.dirty.set(true);
  }

  /** Live connection state for a saved server, from the status endpoint (null while unsaved/unknown). */
  haServerConnected(id: string): boolean {
    return (this.integrations.haServers.value() ?? []).some(s => s.id === id && s.connected);
  }

  // ── Wave Link settings ──────────────────────────────────────────────────────
  /** Merge one field into the current Wave Link settings and persist the whole object (the PUT replaces it). */
  private saveWavelink(patch: Partial<WaveLinkSettings>, successMsg?: string): void {
    const cur: WaveLinkSettings = this.wavelinkSettings.value()
      ?? { enabled: false, focusVolumeRedirect: true, enforceControlledVolume: false, controlledVolumePercent: 100 };
    this.http.put<void>('/api/settings/wavelink', { ...cur, ...patch }).subscribe({
      next: () => {
        this.wavelinkSettings.reload();
        this.integrations.waveLink.reload();
        this.integrations.waveLinkSettings.reload(); // keep Home's integration list in sync
        if (successMsg) this.toast.show(successMsg, { kind: 'success' });
      },
      error: () => this.toast.show('Could not update Wave Link', { kind: 'error' }),
    });
  }

  setWavelinkEnabled(on: boolean): void { this.saveWavelink({ enabled: on }, on ? 'Wave Link enabled' : 'Wave Link disabled'); }
  setWavelinkFocusRedirect(on: boolean): void { this.saveWavelink({ focusVolumeRedirect: on }); }
  setWavelinkEnforceVolume(on: boolean): void { this.saveWavelink({ enforceControlledVolume: on }); }
  setWavelinkControlledPercent(pct: number): void {
    this.saveWavelink({ controlledVolumePercent: Math.max(0, Math.min(100, Math.round(pct || 0))) });
  }

  // ── Sonar settings ──────────────────────────────────────────────────────────
  /**
   * Sends the whole settings object, so changing one field never resets the other. The local copy takes the
   * new state before the request goes out, so a second save made while the first is in flight builds on it
   * rather than on the last state read from the server. A failed save reloads the server's state.
   */
  private saveSonar(patch: Partial<SonarSettings>, successMsg?: string): void {
    const cur = this.sonarSettings.value();
    const next: SonarSettings = {
      enabled: cur?.enabled ?? false,
      updatesPerSecond: cur?.updatesPerSecond ?? SONAR_DEFAULT_RATE,
      ...patch,
    };
    this.sonarSettings.set(next);
    this.http.put<void>('/api/settings/sonar', next).subscribe({
      next: () => {
        this.sonarSettings.reload();
        this.integrations.sonarStatus.reload();
        if (successMsg) this.toast.show(successMsg, { kind: 'success' });
      },
      error: () => {
        this.sonarSettings.reload();
        this.toast.show('Could not update SteelSeries Sonar', { kind: 'error' });
      },
    });
  }

  setSonarEnabled(on: boolean): void {
    this.saveSonar({ enabled: on }, on ? 'SteelSeries Sonar enabled' : 'SteelSeries Sonar disabled');
  }

  /** Saved once, when the slider is released, and only when the value changed. */
  setSonarRate(rate: number): void {
    if (rate === (this.sonarSettings.value()?.updatesPerSecond ?? SONAR_DEFAULT_RATE)) return;
    this.saveSonar({ updatesPerSecond: rate });
  }

  /** User-facing name for a Sonar mode id ('stream'/'classic' as reported by the status endpoint). */
  sonarModeLabel(mode: string | null | undefined): string {
    return mode === 'stream' ? 'Streamer' : mode === 'classic' ? 'Classic' : '';
  }

  // ── Discord settings ─────────────────────────────────────────────────────────
  patchDiscord<K extends keyof DiscordSettings>(key: K, value: DiscordSettings[K]): void {
    const cur = this.discordDraft();
    if (!cur) return;
    this.discordDraft.set({ ...cur, [key]: value });
    this.discordDirty.set(true);
  }

  saveDiscord(): void {
    const dto = this.discordDraft();
    if (!dto) return;
    this.http.put<void>('/api/settings/discord', { ...dto, clientSecret: this.stripSecretMask(dto.clientSecret) }).subscribe({
      next: () => {
        this.discordDirty.set(false);
        this.discordSettings.reload();
        this.integrations.discordStatus.reload();
        this.toast.show('Discord settings saved', { kind: 'success' });
      },
      error: () => this.toast.show('Could not save Discord settings', { kind: 'error' }),
    });
  }

  /**
   * Persist the credentials and start the authorize flow. The backend returns immediately (the flow waits
   * for you to approve a consent popup inside Discord), so we poll the status until it reports authenticated.
   */
  authorizeDiscord(): void {
    const cur = this.discordDraft();
    if (!cur || this.discordAuthorizing()) return;
    // Authorizing implies enabling — persisting the token fires a settings event, and leaving Discord
    // disabled there would tear down the very connection we're authorizing.
    const dto: DiscordSettings = { ...cur, enabled: true };
    this.discordDraft.set(dto);
    this.discordAuthorizing.set(true);
    this.http.put<void>('/api/settings/discord', { ...dto, clientSecret: this.stripSecretMask(dto.clientSecret) }).subscribe({
      next: () => {
        this.discordDirty.set(false);
        this.discordSettings.reload();
        this.http.post('/api/discord/authorize', {}).subscribe({
          next: () => {
            this.toast.show('Approve the “PCPanel” popup inside Discord…', { kind: 'info' });
            this.pollDiscordAuth(0);
          },
          error: () => {
            this.discordAuthorizing.set(false);
            this.toast.show('Could not start Discord authorization', { kind: 'error' });
          },
        });
      },
      error: () => {
        this.discordAuthorizing.set(false);
        this.toast.show('Could not save Discord settings', { kind: 'error' });
      },
    });
  }

  /** Poll status (~every 1.5s, up to ~2 min) until authenticated, while the user approves the Discord popup. */
  private pollDiscordAuth(attempt: number): void {
    this.http.get<DiscordStatusDto>('/api/discord/status').subscribe({
      next: st => {
        this.integrations.discordStatus.reload();
        if (st.authenticated) {
          this.discordAuthorizing.set(false);
          this.integrations.discordUsers.reload();
          this.toast.show('Discord connected', { kind: 'success' });
        } else if (attempt >= 80) {
          this.discordAuthorizing.set(false);
          this.toast.show('Discord not authorized yet — approve the popup in Discord, then try again', { kind: 'warn' });
        } else {
          setTimeout(() => this.pollDiscordAuth(attempt + 1), 1500);
        }
      },
      error: () => {
        if (attempt >= 80) this.discordAuthorizing.set(false);
        else setTimeout(() => this.pollDiscordAuth(attempt + 1), 1500);
      },
    });
  }

  /** Remove the stored authorization and disconnect, so the user can re-authorize cleanly. Keeps the client id/secret. */
  signOutDiscord(): void {
    this.http.post('/api/discord/sign-out', {}).subscribe({
      next: () => {
        this.discordAuthorizing.set(false);
        this.integrations.discordStatus.reload();
        this.integrations.discordUsers.reload();
        this.discordConnOpen.set(true); // reveal the credential fields so they can re-authorize at once
        this.toast.show('Discord credentials removed — you can authorize again', { kind: 'info' });
      },
      error: () => this.toast.show('Could not remove Discord credentials', { kind: 'error' }),
    });
  }

  // ── Focus Volume overrides ───────────────────────────────────────────────────
  // Each override is a rule: when one of its source apps has focus, the focused-app volume dial drives
  // the rule's targets (any volume command — process / device / Wave Link / OBS / …) instead. The target
  // editors reuse the same command picker + field editor as the control page, so a target can be anything
  // the app can set a volume on.

  /** Command types not offered as override targets: Brightness reads the live control position rather than
   *  a fed value, so a synthesized focus value can't drive it (it stays available on the control page). */
  readonly focusTargetExclude = ['com.getpcpanel.commands.command.CommandBrightness'];

  /** Source apps available to pick (running processes), shared with the command editor's app picker. */
  readonly processItems = computed(() => this.integrations.processItems());
  /**
   * Re-reads the running-application list before the picker shows it: the list is fetched once per page
   * load and cached, so without this it shows whatever was running when the page opened (#151).
   */
  refreshProcesses(): void { this.integrations.refreshProcesses(); }
  /** Which target's editor is expanded, keyed "{ruleIdx}:{targetIdx}"; null = all collapsed. */
  readonly fvExpanded = signal<string | null>(null);
  /** Which rule's "add source app" picker overlay is open (rule index), or null. */
  readonly fvAppsOpen = signal<number | null>(null);

  /** While a rule's "detect focused app" countdown is running: its rule index, else null. */
  readonly fvDetecting = signal<number | null>(null);
  /** Seconds left on the detect countdown (gives you time to alt-tab to the target window). */
  readonly fvDetectCountdown = signal(0);

  // ── Curves ─────────────────────────────────────────────────────────────────
  /** Which curve's editor is open, by id; null = the list is collapsed. */
  readonly curveOpen = signal<string | null>(null);

  curves(): CurveDefinition[] { return this.local()?.curves ?? []; }

  curveIsBuiltIn(curve: CurveDefinition): boolean { return isBuiltIn(curve.id); }

  /** A built-in is back to its default when nothing about its shape differs from the shipped one. */
  curveIsDefault(curve: CurveDefinition): boolean {
    const shipped = BUILT_IN_DEFAULTS.find(d => d.id === curve.id);
    if (!shipped) return false;
    return (curve.mode ?? 'amount') === shipped.mode
      && curve.amount === shipped.amount
      && !(curve.points ?? []).length;
  }

  curveSummary(curve: CurveDefinition): string {
    if ((curve.mode ?? 'amount') === 'points') return `${(curve.points ?? []).length} points`;
    if (curve.amount === 0) return 'Linear';
    return `Amount ${curve.amount > 0 ? '+' : ''}${curve.amount}`;
  }

  toggleCurve(id: string): void { this.curveOpen.set(this.curveOpen() === id ? null : id); }

  updateCurve(next: CurveDefinition): void {
    this.patch('curves', this.curves().map(c => (c.id === next.id ? next : c)));
  }

  addCurve(): void {
    const id = `curve-${Date.now().toString(36)}`;
    const created: CurveDefinition = { id, name: 'New curve', mode: 'amount', amount: 25, points: [] };
    this.patch('curves', [...this.curves(), created]);
    this.curveOpen.set(id);
  }

  duplicateCurve(source: CurveDefinition): void {
    const id = `curve-${Date.now().toString(36)}`;
    const copy: CurveDefinition = { ...source, id, name: `${source.name ?? source.id} copy` };
    this.patch('curves', [...this.curves(), copy]);
    this.curveOpen.set(id);
  }

  /** The curve waiting on a confirmed delete; null when nothing is pending. */
  readonly curvePendingDelete = signal<CurveDefinition | null>(null);

  confirmRemoveCurve(): void {
    const curve = this.curvePendingDelete();
    if (!curve) return;
    this.patch('curves', this.curves().filter(c => c.id !== curve.id));
    if (this.curveOpen() === curve.id) this.curveOpen.set(null);
    this.curvePendingDelete.set(null);
  }

  /** Puts a retuned built-in back to the shape the app ships with. */
  resetCurve(curve: CurveDefinition): void {
    const shipped = BUILT_IN_DEFAULTS.find(d => d.id === curve.id);
    if (shipped) this.updateCurve({ ...shipped });
  }

  fvOverrides(): FocusVolumeOverride[] { return this.local()?.focusVolumeOverrides ?? []; }
  fvTargets(rule: FocusVolumeOverride): FocusVolumeTarget[] { return rule?.targets ?? []; }
  fvKey(ri: number, ti: number): string { return ri + ':' + ti; }

  private setFvOverrides(list: FocusVolumeOverride[]): void { this.patch('focusVolumeOverrides', list); }

  private updateFvRule(i: number, patch: Partial<FocusVolumeOverride>): void {
    this.setFvOverrides(this.fvOverrides().map((r, k) => (k === i ? { ...r, ...patch } : r)));
  }

  addFvRule(): void {
    this.setFvOverrides([...this.fvOverrides(), { sources: [], targets: [], includeSource: false }]);
  }

  removeFvRule(i: number): void {
    this.setFvOverrides(this.fvOverrides().filter((_, k) => k !== i));
  }

  setFvSources(i: number, sources: string[]): void { this.updateFvRule(i, { sources }); }
  setFvIncludeSource(i: number, on: boolean): void { this.updateFvRule(i, { includeSource: on }); }

  /**
   * Capture whichever app the OS reports as focused after a short countdown, and add its exe name as a
   * source. This is the reliable way to get the right source: the focused process isn't always the one
   * you'd guess — Steam's window is owned by steamwebhelper.exe, Electron/Discord by a helper, etc. The
   * countdown gives you time to alt-tab to the target window (focusing this UI would just capture the
   * browser).
   */
  detectFocusedSource(ruleIndex: number): void {
    if (this.fvDetecting() !== null) return;
    this.fvDetecting.set(ruleIndex);
    let n = 3;
    this.fvDetectCountdown.set(n);
    const tick = (): void => {
      n -= 1;
      if (n > 0) { this.fvDetectCountdown.set(n); setTimeout(tick, 1000); return; }
      this.http.get<{ liveFocusApplication?: string | null; focusUnavailableReason?: string | null }>('/api/focus-volume/diagnostics').subscribe({
        next: r => {
          const app = r.liveFocusApplication;
          const base = app ? app.split(/[\\/]/).pop() ?? '' : '';
          const cur = this.fvOverrides()[ruleIndex];
          if (base && cur && !(cur.sources ?? []).some(s => s.toLowerCase() === base.toLowerCase())) {
            this.setFvSources(ruleIndex, [...(cur.sources ?? []), base]);
            this.toast.show(`Added focused app: ${base}`, { kind: 'success' });
          } else if (!base) {
            // The backend knows *why* — on Linux that is usually "this desktop session has no API for it"
            // rather than anything the user did wrong. Say so instead of the bare failure (#151).
            this.toast.show(r.focusUnavailableReason ?? 'Could not read the focused app', { kind: 'error' });
          }
          this.fvDetecting.set(null);
        },
        error: () => { this.toast.show('Could not read the focused app', { kind: 'error' }); this.fvDetecting.set(null); },
      });
    };
    setTimeout(tick, 1000);
  }

  removeFvSource(i: number, app: string): void {
    this.setFvSources(i, (this.fvOverrides()[i]?.sources ?? []).filter(s => s !== app));
  }

  private setFvTargets(i: number, targets: FocusVolumeTarget[]): void { this.updateFvRule(i, { targets }); }

  /** Append a fresh instance of the chosen command type to rule i's targets, and expand it for editing. */
  addFvTarget(i: number, def: CommandDef): void {
    const targets = [...this.fvTargets(this.fvOverrides()[i]), { command: def.buildEmpty() as Cmd } as FocusVolumeTarget];
    this.setFvTargets(i, targets);
    this.fvExpanded.set(this.fvKey(i, targets.length - 1));
  }

  removeFvTarget(i: number, j: number): void {
    this.setFvTargets(i, this.fvTargets(this.fvOverrides()[i]).filter((_, k) => k !== j));
  }

  setFvTargetCommand(i: number, j: number, command: Cmd): void {
    this.setFvTargets(i, this.fvTargets(this.fvOverrides()[i]).map((t, k) => (k === j ? { command } as FocusVolumeTarget : t)));
  }

  toggleFvTarget(i: number, j: number): void {
    const key = this.fvKey(i, j);
    this.fvExpanded.set(this.fvExpanded() === key ? null : key);
  }

  fvTargetDef(t: FocusVolumeTarget): CommandDef | undefined {
    const type = (t?.command as Cmd)?.['_type'];
    return type ? COMMAND_BY_TYPE.get(type) : undefined;
  }
  fvTargetLabel(t: FocusVolumeTarget): string {
    return this.fvTargetDef(t)?.label ?? String((t?.command as Cmd)?.['_type'] ?? '').split('.').pop() ?? 'Target';
  }
  fvTargetIcon(t: FocusVolumeTarget): IconName { return this.fvTargetDef(t)?.icon ?? 'volume'; }

  // ── New apps at their control's level ─────────────────────────────────────
  readonly newAppExceptionsOpen = signal(false);

  removeNewAppException(app: string): void {
    this.patch('newAppsAtDialLevelExceptions', (this.local()?.newAppsAtDialLevelExceptions ?? []).filter(a => a !== app));
  }

  // ── Notification lights ───────────────────────────────────────────────────
  readonly alertTriggerOptions: SegmentOption<string>[] = [
    { value: 'TASKBAR_FLASH', label: 'Taskbar flash' },
    { value: 'MIC_IN_USE', label: 'Microphone in use' },
    { value: 'NOTIFICATION', label: 'Shows a notification' },
    { value: 'WINDOW_TITLE', label: 'Its window title contains' },
  ];

  /** Triggers read from Windows or Linux only (notifications, window titles). */
  private static readonly NOT_ON_MAC = new Set(['NOTIFICATION', 'WINDOW_TITLE']);

  /** The triggers offered on this platform, plus the alert's own so a saved one still shows. */
  alertTriggerOptionsFor(alert: NotificationAlert): SegmentOption<string>[] {
    if (this.platform.os() !== 'mac') return this.alertTriggerOptions;
    return this.alertTriggerOptions.filter(o => !SettingsComponent.NOT_ON_MAC.has(o.value) || o.value === alert.trigger);
  }

  alertTriggerLabel(alert: NotificationAlert): string {
    return this.alertTriggerOptions.find(o => o.value === alert.trigger)?.label ?? alert.trigger;
  }

  /** Notification senders the app has seen, for an alert's source. */
  readonly notificationSources = signal<string[]>([]);

  refreshNotificationSources(): void {
    this.limits.reload();
    this.http.get<string[]>('/api/alerts/notification-sources').subscribe({
      next: sources => this.notificationSources.set(sources ?? []),
      error: () => {},
    });
  }

  /** The seen senders plus the alert's own source, after a choice to go back to matching by app name. */
  notificationSourceOptions(alert: NotificationAlert): SelectOption<string>[] {
    const sources = [...this.notificationSources()];
    if (alert.source && !sources.some(s => s.toLowerCase() === alert.source!.toLowerCase())) sources.unshift(alert.source);
    return [{ value: '', label: 'From the app name' }, ...sources.map(s => ({ value: s, label: s }))];
  }
  readonly alertTargetOptions: SelectOption<string>[] = [
    ...[0, 1, 2, 3, 4].map(i => ({ value: `knob:${i}`, label: `Knob ${i + 1}` })),
    ...[0, 1, 2, 3].map(i => ({ value: `slider:${i}`, label: `Slider ${i + 1}` })),
    { value: 'logo', label: 'Logo' },
  ];
  readonly alertAppOptions = computed<SelectOption<string>[]>(() =>
    this.integrations.processItems().map(p => ({ value: p.key, label: p.label })));

  alerts(): NotificationAlert[] { return this.local()?.notificationAlerts ?? []; }

  readonly alertEffectOptions: SegmentOption<AlertEffect>[] = [
    { value: 'STEADY', label: 'Steady' },
    { value: 'BLINK', label: 'Blink' },
    { value: 'PULSE', label: 'Pulse' },
  ];

  alertEffect(alert: NotificationAlert): AlertEffect { return alert.effect ?? (alert.blink ? 'BLINK' : 'STEADY'); }

  readonly alertBlinkOptions: SegmentOption<'OFF' | 'COLOR'>[] = [
    { value: 'OFF', label: 'Off' },
    { value: 'COLOR', label: 'Colour' },
  ];

  /** Off leaves a blink's second half dark; a colour starts at white, to be picked next. */
  setAlertBlinkColor(i: number, mode: 'OFF' | 'COLOR'): void {
    const alert = this.alerts()[i];
    if (!alert) return;
    if (mode === 'OFF') this.patchAlert(i, { blinkColor: undefined });
    else if (!alert.blinkColor) this.patchAlert(i, { blinkColor: '#FFFFFF' });
  }

  readonly alertBrightnessOptions: SegmentOption<'PANEL' | 'OWN'>[] = [
    { value: 'PANEL', label: 'As the panel' },
    { value: 'OWN', label: 'Own' },
  ];

  /** As the panel follows the panel's brightness; its own starts at full, to be lowered with the slider. */
  setAlertBrightnessMode(i: number, mode: 'PANEL' | 'OWN'): void {
    const alert = this.alerts()[i];
    if (!alert) return;
    if (mode === 'PANEL') this.patchAlert(i, { brightness: undefined });
    else if (alert.brightness == null) this.patchAlert(i, { brightness: 100 });
  }

  /**
   * Sets the effect; {@code blink} follows it so an older version still blinks a blinking light, and the period
   * in effect is written down so an older save's blink keeps its rhythm.
   */
  setAlertEffect(i: number, effect: AlertEffect): void {
    const alert = this.alerts()[i];
    if (!alert) return;
    this.patchAlert(i, { effect, blink: effect === 'BLINK', periodMs: this.alertPeriod(alert) });
  }

  addAlert(): void {
    this.patch('notificationAlerts', [...this.alerts(), { trigger: 'TASKBAR_FLASH', app: '', target: 'knob:0', color: '#8A5CFF', blink: true, effect: 'BLINK', stopAfterSeconds: 0, disabled: false }]);
  }

  /** A red light that is on while any app uses the microphone (no app = any app), on the logo when a panel has one. */
  addOnAirAlert(): void {
    const hasLogo = Object.values(this.state.devices()).some(d => d.hasLogoLed);
    this.patch('notificationAlerts', [...this.alerts(), {
      trigger: 'MIC_IN_USE', target: hasLogo ? 'logo' : 'knob:0', color: '#ff1a1a', blink: false, effect: 'STEADY', disabled: false,
    }]);
  }

  /** Whether the notification lights differ from the saved ones; the panel only knows the saved list. */
  readonly alertsEdited = computed(() =>
    JSON.stringify(this.local()?.notificationAlerts ?? []) !== JSON.stringify(this.settings.value()?.notificationAlerts ?? []));

  /** Whether alert {@code i} is showing on the panel now; only known for the saved list, so not while it is edited. */
  alertShowing(i: number): boolean { return !this.alertsEdited() && this.state.alertsLit().includes(i); }

  /** The period in effect, as the backend reads it: an older save's blink keeps its 1000 ms rhythm. */
  alertPeriod(alert: NotificationAlert): number { return alert.periodMs ?? (alert.effect == null && alert.blink ? 1000 : 1200); }

  /** Takes a typed period, kept within 200–10000 ms; a cleared or unreadable field keeps the previous period. */
  setAlertPeriod(i: number, input: HTMLInputElement): void {
    const alert = this.alerts()[i];
    if (!alert) return;
    const previous = this.alertPeriod(alert);
    const typed = input.value.trim() === '' ? NaN : Number(input.value);
    const periodMs = Number.isFinite(typed) ? Math.min(10000, Math.max(200, Math.round(typed))) : previous;
    input.value = String(periodMs);
    if (periodMs !== previous) this.patchAlert(i, { periodMs });
  }

  previewAlert(i: number): void {
    this.http.post<void>(`/api/alerts/${i}/preview`, {}).subscribe({
      error: () => this.toast.show('Could not show the light', { kind: 'error' }),
    });
  }

  removeAlert(i: number): void { this.patch('notificationAlerts', this.alerts().filter((_, k) => k !== i)); }

  /** Which alert's "except these apps" picker is open. */
  readonly alertExceptOpen = signal<number | null>(null);

  removeAlertExcept(i: number, app: string): void {
    this.patchAlert(i, { exceptApps: (this.alerts()[i]?.exceptApps ?? []).filter(a => a !== app) });
  }

  patchAlert(i: number, part: Partial<NotificationAlert>): void {
    this.patch('notificationAlerts', this.alerts().map((a, k) => (k === i ? { ...a, ...part } : a)));
  }

  // ── Panel test & start-up animation ───────────────────────────────────────
  readonly panelTestOpen = signal(false);

  previewStartupAnimation(): void {
    this.http.post<void>('/api/panel-test/startup-animation', {}).subscribe({
      error: () => this.toast.show('Could not play the animation', { kind: 'error' }),
    });
  }

  // ── Backups ────────────────────────────────────────────────────────────────
  readonly backups = httpResource<SaveBackup[]>(() => '/api/settings/backups');
  /** The backup waiting on a confirmed restore; null when nothing is pending. */
  readonly backupPendingRestore = signal<SaveBackup | null>(null);

  backupDate(ts: number): string { return new Date(ts).toLocaleString(); }
  backupSize(bytes: number): string { return `${Math.max(1, Math.round(bytes / 1024))} KB`; }

  restoreBackup(): void {
    const b = this.backupPendingRestore();
    if (!b) return;
    this.backupPendingRestore.set(null);
    this.http.post<void>(`/api/settings/backups/${encodeURIComponent(b.name)}/restore`, {}).subscribe({
      next: () => {
        this.dirty.set(false);
        this.settings.reload();
        this.backups.reload();
        this.toast.show('Backup restored', { kind: 'success' });
      },
      error: () => this.toast.show('Could not restore the backup', { kind: 'error' }),
    });
  }

  // ── save ────────────────────────────────────────────────────────────────────
  /** Copy of the settings with masked secrets replaced by '' — never send the mask back as a value. */
  private sanitizeSecrets(dto: SettingsDto): SettingsDto {
    return {
      ...dto,
      obsPassword: this.stripSecretMask(dto.obsPassword),
      mqtt: { ...dto.mqtt, username: this.stripSecretMask(dto.mqtt.username), password: this.stripSecretMask(dto.mqtt.password) },
      homeAssistantServers: (dto.homeAssistantServers ?? []).map(s => ({ ...s, token: this.stripSecretMask(s.token) })),
    };
  }

  save(thenLeave = false): void {
    const dto = this.local();
    if (!dto || this.saving()) return;
    this.saving.set(true);
    const openedWindow = dto.appWindow && !this.settings.value()?.appWindow;
    this.settingsService.updateSettings(this.sanitizeSecrets(dto)).subscribe({
      next: () => {
        this.saving.set(false);
        this.dirty.set(false);
        this.confirmLeaveOpen.set(false);
        this.settings.reload();
        this.integrations.reload();
        this.history.checkpoint(); // a saved settings form is one undo step
        this.history.refreshSoon();
        this.toast.show('Settings saved', { kind: 'success' });
        if (openedWindow) this.toast.show('PCPanel opened in its own window', { sub: 'You can close this tab.' });
        if (thenLeave) this.router.navigate(['/']);
      },
      error: () => {
        this.saving.set(false);
        this.toast.show('Could not save settings', { kind: 'error' });
      },
    });
  }

  // ── leave guard ──────────────────────────────────────────────────────────────
  back(): void {
    if (this.dirty()) { this.confirmLeaveOpen.set(true); return; }
    this.router.navigate(['/']);
  }

  saveAndLeave(): void { this.save(true); }

  leaveWithoutSaving(): void {
    this.confirmLeaveOpen.set(false);
    this.dirty.set(false);
    this.router.navigate(['/']);
  }

  stayHere(): void { this.confirmLeaveOpen.set(false); }

  // ── quit ──────────────────────────────────────────────────────────────────────
  /** Ask the backend to shut down. The server stops right after replying, so a completed POST and a
   *  network error (the socket dropping as it goes down) both mean "shutting down" — show the stopped
   *  overlay either way. */
  quitApp(): void {
    if (this.quitState() !== 'idle') return;
    this.quitConfirmOpen.set(false);
    this.quitState.set('quitting');
    this.http.post('/api/system/quit', {}).subscribe({
      next: () => this.quitState.set('stopped'),
      error: () => this.quitState.set('stopped'),
    });
  }
}
