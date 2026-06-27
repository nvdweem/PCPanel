import { ChangeDetectionStrategy, Component, computed, effect, HostListener, inject, signal, untracked } from '@angular/core';
import { OverlayModule } from '@angular/cdk/overlay';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, httpResource } from '@angular/common/http';
import { SettingsService } from '../../services/settings.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { PlatformService } from '../../services/platform.service';
import { DebugService, DeviceTypeOverride, OsOverride } from '../../services/debug.service';
import {
  FocusVolumeOverride, FocusVolumeTarget, OverlayPosition, SettingsDto, WaveLinkSettings,
} from '../../models/generated/backend.types';
import {
  AppPickerComponent,
  ColorPickerComponent, IconComponent, IconName, ModalComponent, SegmentedComponent, SegmentOption,
  SelectComponent, SelectOption, SliderComponent, SpinnerComponent, StatusDotComponent, StatusKind, ToastService, ToggleComponent,
} from '../../ui';
import { CommandPickerComponent } from '../../features/commands/command-picker.component';
import { CommandFieldsComponent } from '../../features/commands/command-fields.component';
import { COMMAND_BY_TYPE, CommandDef } from '../../features/commands/command-catalog';

type Cmd = Record<string, any>;
type TabId = 'general' | 'focusoverride' | 'obs' | 'voicemeeter' | 'wavelink' | 'osc' | 'mqtt' | 'homeassistant' | 'overlay' | 'debug';
interface TabDef { id: TabId; label: string; integration?: 'obs' | 'voicemeeter' | 'wavelink'; supported?: boolean; }

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    IconComponent, StatusDotComponent, SpinnerComponent, ToggleComponent,
    SegmentedComponent, SliderComponent, ColorPickerComponent, ModalComponent, SelectComponent,
    OverlayModule, AppPickerComponent, CommandPickerComponent, CommandFieldsComponent,
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

  readonly activeTab = signal<TabId>('general');
  readonly saving = signal(false);
  readonly confirmLeaveOpen = signal(false);

  readonly quitConfirmOpen = signal(false);
  /** Drives the Quit button and the post-shutdown overlay: idle → quitting → stopped. */
  readonly quitState = signal<'idle' | 'quitting' | 'stopped'>('idle');

  /** Local editable copy, seeded once from the resource (or when not dirty). */
  readonly local = signal<SettingsDto | null>(null);
  readonly dirty = signal(false);

  /** Object-URL of the backend-rendered overlay preview (the real renderer → PNG), or null. */
  readonly overlayPreviewUrl = signal<string | null>(null);
  private previewObjUrl: string | null = null;
  private previewTimer: ReturnType<typeof setTimeout> | null = null;

  // OSC add-form fields
  readonly oscHost = signal('');
  readonly oscPort = signal('');

  // Home Assistant add-form fields
  readonly haName = signal('');
  readonly haUrl = signal('');
  readonly haToken = signal('');

  private readonly allTabs: TabDef[] = [
    { id: 'general', label: 'General' },
    { id: 'focusoverride', label: 'Focus Override' },
    { id: 'overlay', label: 'Overlay' },
    { id: 'obs', label: 'OBS Studio', integration: 'obs' },
    { id: 'voicemeeter', label: 'Voicemeeter', integration: 'voicemeeter' },
    { id: 'wavelink', label: 'Wave Link', integration: 'wavelink' },
    { id: 'osc', label: 'OSC' },
    { id: 'mqtt', label: 'MQTT' },
    { id: 'homeassistant', label: 'Home Assistant' },
    { id: 'debug', label: 'Debug' },
  ];

  /** All tabs shown; platform-specific ones flagged unsupported (shown disabled). */
  readonly tabs = computed<TabDef[]>(() => this.allTabs.map(t => ({ ...t, supported: this.tabSupported(t.id) })));

  tabSupported(id: TabId): boolean {
    if (id === 'voicemeeter') return this.platform.voicemeeterSupported();
    if (id === 'wavelink') return this.platform.waveLinkSupported();
    return true;
  }

  platformNote(id: TabId): string {
    if (id === 'voicemeeter') return 'Voicemeeter is only available on Windows.';
    if (id === 'wavelink') return 'Elgato Wave Link is only available on Windows and macOS.';
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

  // Font families the backend (Java2D) can actually render — the overlay font picker only offers these.
  readonly overlayFonts = httpResource<string[]>(() => '/api/overlay/fonts');
  readonly fontOptions = computed<SelectOption<string>[]>(() => {
    const fonts = this.overlayFonts.value() ?? [];
    // Render each option's label in its own family so the picker previews the font (the local browser
    // has the same system fonts the backend enumerates).
    return [{ value: '', label: 'Default (Segoe UI)', font: 'Segoe UI' }, ...fonts.map(f => ({ value: f, label: f, font: f }))];
  });

  /** Position the overlay live-preview card per overlayPosition + (scaled) padding. */
  readonly overlayPreviewStyle = computed<Record<string, string>>(() => {
    const pos = this.local()?.overlayPosition ?? 'bottomRight';
    const pad = Math.max(6, Math.min(36, Math.round((this.local()?.overlayPadding ?? 16) * 0.6))) + 'px';
    const st: Record<string, string> = {};
    const tf: string[] = [];
    if (pos.startsWith('top')) st['top'] = pad;
    else if (pos.startsWith('bottom')) st['bottom'] = pad;
    else { st['top'] = '50%'; tf.push('translateY(-50%)'); }
    if (pos.endsWith('Left')) st['left'] = pad;
    else if (pos.endsWith('Right')) st['right'] = pad;
    else { st['left'] = '50%'; tf.push('translateX(-50%)'); }
    if (tf.length) st['transform'] = tf.join(' ');
    return st;
  });

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

    // Deep-link support: /settings?tab=homeassistant (used by the action editor's "Manage servers").
    const tab = this.route.snapshot.queryParamMap.get('tab') as TabId | null;
    if (tab && this.allTabs.some(t => t.id === tab)) this.activeTab.set(tab);

    // Live overlay preview: render the real overlay on the backend from the (possibly unsaved) settings,
    // debounced so dragging a slider doesn't fire a request per tick. Guarantees the preview can't drift
    // from what the on-screen overlay actually draws.
    effect(() => {
      const s = this.local();
      const onOverlay = this.activeTab() === 'overlay';
      const supported = this.overlayStylingSupported();
      if (!s || !onOverlay || !supported) return;
      untracked(() => this.scheduleOverlayPreview(s));
    });
  }

  private scheduleOverlayPreview(s: SettingsDto): void {
    if (this.previewTimer) clearTimeout(this.previewTimer);
    this.previewTimer = setTimeout(() => {
      this.http.post('/api/overlay/preview', s, { responseType: 'blob' }).subscribe({
        next: blob => this.setPreviewUrl(blob.size ? URL.createObjectURL(blob) : null),
        error: () => this.setPreviewUrl(null),
      });
    }, 100);
  }

  private setPreviewUrl(url: string | null): void {
    if (this.previewObjUrl) URL.revokeObjectURL(this.previewObjUrl);
    this.previewObjUrl = url;
    this.overlayPreviewUrl.set(url);
  }

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
      case 'osc': return this.oscTabStatus();
      case 'mqtt': return this.mqttTabStatus();
      case 'homeassistant': return this.haTabStatus();
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
  /** Which target's editor is expanded, keyed "{ruleIdx}:{targetIdx}"; null = all collapsed. */
  readonly fvExpanded = signal<string | null>(null);
  /** Which rule's "add source app" picker overlay is open (rule index), or null. */
  readonly fvAppsOpen = signal<number | null>(null);

  /** While a rule's "detect focused app" countdown is running: its rule index, else null. */
  readonly fvDetecting = signal<number | null>(null);
  /** Seconds left on the detect countdown (gives you time to alt-tab to the target window). */
  readonly fvDetectCountdown = signal(0);

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
      this.http.get<{ liveFocusApplication?: string | null }>('/api/focus-volume/diagnostics').subscribe({
        next: r => {
          const app = r.liveFocusApplication;
          const base = app ? app.split(/[\\/]/).pop() ?? '' : '';
          const cur = this.fvOverrides()[ruleIndex];
          if (base && cur && !(cur.sources ?? []).some(s => s.toLowerCase() === base.toLowerCase())) {
            this.setFvSources(ruleIndex, [...(cur.sources ?? []), base]);
            this.toast.show(`Added focused app: ${base}`, { kind: 'success' });
          } else if (!base) {
            this.toast.show('Could not read the focused app', { kind: 'error' });
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

  // ── save ────────────────────────────────────────────────────────────────────
  save(thenLeave = false): void {
    const dto = this.local();
    if (!dto || this.saving()) return;
    this.saving.set(true);
    this.settingsService.updateSettings(dto).subscribe({
      next: () => {
        this.saving.set(false);
        this.dirty.set(false);
        this.confirmLeaveOpen.set(false);
        this.settings.reload();
        this.integrations.reload();
        this.toast.show('Settings saved', { kind: 'success' });
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
