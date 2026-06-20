import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, httpResource } from '@angular/common/http';
import { SettingsService } from '../../services/settings.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { PlatformService } from '../../services/platform.service';
import {
  OverlayPosition, SettingsDto,
} from '../../models/generated/backend.types';
import {
  ColorPickerComponent, IconComponent, ModalComponent, SegmentedComponent, SegmentOption,
  SliderComponent, SpinnerComponent, StatusDotComponent, StatusKind, ToastService, ToggleComponent,
} from '../../ui';

type TabId = 'general' | 'obs' | 'voicemeeter' | 'wavelink' | 'osc' | 'mqtt' | 'overlay';
interface TabDef { id: TabId; label: string; integration?: 'obs' | 'voicemeeter' | 'wavelink'; supported?: boolean; }

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    IconComponent, StatusDotComponent, SpinnerComponent, ToggleComponent,
    SegmentedComponent, SliderComponent, ColorPickerComponent, ModalComponent,
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
  private readonly http = inject(HttpClient);
  readonly platform = inject(PlatformService);

  readonly settings = this.settingsService.settings;

  readonly activeTab = signal<TabId>('general');
  readonly saving = signal(false);
  readonly confirmLeaveOpen = signal(false);

  /** Local editable copy, seeded once from the resource (or when not dirty). */
  readonly local = signal<SettingsDto | null>(null);
  readonly dirty = signal(false);

  // OSC add-form fields
  readonly oscHost = signal('');
  readonly oscPort = signal('');

  private readonly allTabs: TabDef[] = [
    { id: 'general', label: 'General' },
    { id: 'obs', label: 'OBS Studio', integration: 'obs' },
    { id: 'voicemeeter', label: 'Voicemeeter', integration: 'voicemeeter' },
    { id: 'wavelink', label: 'Wave Link', integration: 'wavelink' },
    { id: 'osc', label: 'OSC' },
    { id: 'mqtt', label: 'MQTT' },
    { id: 'overlay', label: 'Overlay' },
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

  // Separate tiny Wave Link enable resource (not part of SettingsDto).
  readonly wavelinkEnabled = httpResource<{ enabled: boolean }>(() => '/api/settings/wavelink');

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
    this.local.set({ ...cur, oscConnections: [...cur.oscConnections, { host, port }] });
    this.dirty.set(true);
    this.oscHost.set('');
    this.oscPort.set('');
  }

  removeOscTarget(i: number): void {
    const cur = this.local();
    if (!cur) return;
    this.local.set({ ...cur, oscConnections: cur.oscConnections.filter((_, k) => k !== i) });
    this.dirty.set(true);
  }

  // ── Wave Link separate enable toggle ────────────────────────────────────────
  setWavelinkEnabled(on: boolean): void {
    this.http.put<void>('/api/settings/wavelink', { enabled: on }).subscribe({
      next: () => {
        this.wavelinkEnabled.reload();
        this.integrations.waveLink.reload();
        this.integrations.waveLinkSettings.reload(); // keep Home's integration list in sync
        this.toast.show(on ? 'Wave Link enabled' : 'Wave Link disabled', { kind: 'success' });
      },
      error: () => this.toast.show('Could not update Wave Link', { kind: 'error' }),
    });
  }

  // ── save ────────────────────────────────────────────────────────────────────
  save(): void {
    const dto = this.local();
    if (!dto || this.saving()) return;
    this.saving.set(true);
    this.settingsService.updateSettings(dto).subscribe({
      next: () => {
        this.saving.set(false);
        this.dirty.set(false);
        this.settings.reload();
        this.integrations.reload();
        this.toast.show('Settings saved', { kind: 'success' });
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

  leaveWithoutSaving(): void {
    this.confirmLeaveOpen.set(false);
    this.dirty.set(false);
    this.router.navigate(['/']);
  }

  stayHere(): void { this.confirmLeaveOpen.set(false); }
}
