import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DeviceStateService } from '../../services/device-state.service';
import { SelectedDeviceService } from '../../services/selected-device.service';
import { DeviceService } from '../../services/device.service';
import { SettingsService } from '../../services/settings.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import {
  ConnectionBadgeComponent, ConnState, IconComponent, ModalComponent,
  SelectComponent, SelectOption, SliderComponent, SpinnerComponent, StatusDotComponent, ToastService,
} from '../../ui';
import { PcDeviceComponent, ControlClick } from '../../devices/visual/pc-device.component';

interface IntegrationRow { name: string; state: 'connected' | 'connecting' | 'reconnecting' | 'error'; stateLabel: string; }

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    IconComponent, StatusDotComponent, ConnectionBadgeComponent, SliderComponent, SelectComponent,
    SpinnerComponent, ModalComponent, PcDeviceComponent,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  private readonly state = inject(DeviceStateService);
  private readonly facade = inject(SelectedDeviceService);
  private readonly deviceService = inject(DeviceService);
  private readonly settings = inject(SettingsService);
  private readonly integrations = inject(IntegrationDataService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly ready = this.state.ready;
  readonly devices = this.facade.devices;
  readonly hasDevices = this.facade.hasDevices;
  readonly selected = this.facade.selected;
  readonly selectedSerial = this.facade.selectedSerial;

  readonly devicesCollapsed = signal(false);
  readonly editingName = signal(false);
  readonly pendingBrightness = signal<number | null>(null);
  readonly newProfileOpen = signal(false);
  readonly newProfileName = signal('');

  readonly connState = computed<ConnState>(() => {
    if (this.state.reconnecting()) return 'reconnecting';
    if (!this.state.connected()) return 'connecting';
    return 'connected';
  });

  readonly brightness = computed(() => this.pendingBrightness() ?? this.selected()?.lightingConfig?.globalBrightness ?? 0);

  readonly profileOptions = computed<SelectOption[]>(() =>
    (this.selected()?.profiles ?? []).map(p => ({ value: p, label: p })));

  readonly currentProfile = computed(() => this.selected()?.currentProfile ?? '');

  readonly integrationRows = computed<IntegrationRow[]>(() => {
    const s = this.settings.settings.value();
    if (!s) return [];
    const rows: IntegrationRow[] = [];
    if (s.obsEnabled) rows.push(this.row('OBS Studio', this.integrations.obsScenes));
    if (s.voicemeeterEnabled) rows.push(this.row('Voicemeeter', this.integrations.vmAdvanced));
    // Wave Link surfaces when its endpoint responds at all.
    const wl = this.integrations.waveLink;
    if (wl.value() || wl.error()) rows.push(this.row('Wave Link', wl));
    return rows;
  });

  private row(name: string, res: { value: () => unknown; error: () => unknown; isLoading: () => boolean }): IntegrationRow {
    if (res.isLoading()) return { name, state: 'connecting', stateLabel: 'connecting' };
    if (res.error()) return { name, state: 'reconnecting', stateLabel: 'retrying' };
    if (res.value()) return { name, state: 'connected', stateLabel: 'connected' };
    return { name, state: 'error', stateLabel: 'offline' };
  }

  // ── actions ────────────────────────────────────────────────────────────────
  select(serial: string): void { this.facade.select(serial); }

  statusFor(serial: string): 'ok' | 'idle' {
    // Every device in the live map is connected; offline ones are removed.
    return this.state.devices()[serial] ? 'ok' : 'idle';
  }

  startRename(): void { this.editingName.set(true); }

  commitRename(value: string): void {
    this.editingName.set(false);
    const serial = this.selectedSerial();
    const name = value.trim();
    if (!serial || !name || name === this.selected()?.displayName) return;
    this.deviceService.renameDevice(serial, name).subscribe({ error: () => this.toast.show('Rename failed', { kind: 'error' }) });
  }

  onBrightnessInput(v: number): void { this.pendingBrightness.set(v); }

  commitBrightness(v: number): void {
    const sel = this.selected();
    if (!sel) return;
    this.deviceService.setLighting(sel.serial, { ...sel.lightingConfig, globalBrightness: v })
      .subscribe({ error: () => this.toast.show('Could not set brightness', { kind: 'error' }) });
    setTimeout(() => this.pendingBrightness.set(null), 500);
  }

  switchProfile(name: string): void {
    const serial = this.selectedSerial();
    if (!serial || name === this.currentProfile()) return;
    this.deviceService.switchProfile(serial, name)
      .subscribe({
        next: () => this.toast.show(`Switched to ${name}`, { kind: 'success' }),
        error: () => this.toast.show('Profile switch failed', { kind: 'error' }),
      });
  }

  createProfile(): void {
    const serial = this.selectedSerial();
    const name = this.newProfileName().trim();
    this.newProfileOpen.set(false);
    this.newProfileName.set('');
    if (!serial || !name) return;
    this.deviceService.createProfile(serial, name).subscribe({
      next: () => this.deviceService.switchProfile(serial, name).subscribe(),
      error: () => this.toast.show('Could not create profile', { kind: 'error' }),
    });
  }

  onControlClick(e: ControlClick): void {
    const serial = this.selectedSerial();
    if (!serial) return;
    if (e.kind === 'logo') { this.router.navigate(['/lighting', serial]); return; }
    this.router.navigate(['/control', serial, e.index]);
  }

  openLighting(): void { const s = this.selectedSerial(); if (s) this.router.navigate(['/lighting', s]); }
  openAdvanced(): void { const s = this.selectedSerial(); if (s) this.router.navigate(['/device', s]); }
  openSettings(): void { this.router.navigate(['/settings']); }
}
