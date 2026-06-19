import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, httpResource } from '@angular/common/http';
import { SettingsService } from '../../services/settings.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import {
  OverlayPosition, SettingsDto,
} from '../../models/generated/backend.types';
import {
  ColorPickerComponent, IconComponent, SegmentedComponent, SegmentOption,
  SliderComponent, SpinnerComponent, StatusDotComponent, StatusKind, ToastService, ToggleComponent,
} from '../../ui';

type TabId = 'general' | 'obs' | 'voicemeeter' | 'wavelink' | 'osc' | 'mqtt' | 'overlay';
interface TabDef { id: TabId; label: string; integration?: 'obs' | 'voicemeeter' | 'wavelink'; }

/** A resource-shaped object as used across the integration httpResources. */
interface ResLike { value: () => unknown; error: () => unknown; isLoading: () => boolean; }

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    IconComponent, StatusDotComponent, SpinnerComponent, ToggleComponent,
    SegmentedComponent, SliderComponent, ColorPickerComponent,
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

  readonly settings = this.settingsService.settings;

  readonly activeTab = signal<TabId>('general');
  readonly saving = signal(false);

  /** Local editable copy, seeded once from the resource (or when not dirty). */
  readonly local = signal<SettingsDto | null>(null);
  readonly dirty = signal(false);

  // OSC add-form fields
  readonly oscHost = signal('');
  readonly oscPort = signal('');

  readonly tabs: TabDef[] = [
    { id: 'general', label: 'General' },
    { id: 'obs', label: 'OBS Studio', integration: 'obs' },
    { id: 'voicemeeter', label: 'Voicemeeter', integration: 'voicemeeter' },
    { id: 'wavelink', label: 'Wave Link', integration: 'wavelink' },
    { id: 'osc', label: 'OSC' },
    { id: 'mqtt', label: 'MQTT' },
    { id: 'overlay', label: 'Overlay' },
  ];

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
  tabStatus(tab: TabDef): StatusKind | null {
    if (!tab.integration) return null;
    const res: ResLike = tab.integration === 'obs' ? this.integrations.obsScenes
      : tab.integration === 'voicemeeter' ? this.integrations.vmAdvanced
        : this.integrations.waveLink;
    if (res.isLoading()) return 'connecting';
    if (res.error()) return 'error';
    return res.value() ? 'ok' : 'idle';
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

  back(): void { this.router.navigate(['/']); }
}
