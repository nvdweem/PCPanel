import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DeviceStateService } from '../../services/device-state.service';
import { SelectedDeviceService } from '../../services/selected-device.service';
import { DeviceService } from '../../services/device.service';
import { SettingsService } from '../../services/settings.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { PlatformService } from '../../services/platform.service';
import {
  BottomBarComponent, ConnectionBadgeComponent, ConnState, IconComponent, ModalComponent,
  SpinnerComponent, StatusDotComponent, ToastService,
} from '../../ui';
import { AddDeviceModalComponent } from './add-device-modal.component';
import { DeviceRendererComponent } from '../../devices/visual/device-renderer.component';
import { ControlClick } from '../../devices/visual/control-click';
import { DeviceSnapshotDto } from '../../models/generated/backend.types';

interface IntegrationRow { name: string; dot: 'ok' | 'idle' | 'connecting'; stateLabel: string; connected: boolean; }

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    IconComponent, StatusDotComponent, ConnectionBadgeComponent, BottomBarComponent,
    SpinnerComponent, ModalComponent, DeviceRendererComponent, AddDeviceModalComponent,
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
  private readonly platform = inject(PlatformService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly ready = this.state.ready;
  readonly devices = this.facade.devices;
  readonly hasDevices = this.facade.hasDevices;
  /** Backend host OS — drives the platform-specific "plugged in but not showing up?" hint. */
  readonly os = this.platform.os;
  readonly selected = this.facade.selected;
  readonly selectedSerial = this.facade.selectedSerial;

  readonly editingName = signal(false);
  readonly newProfileOpen = signal(false);
  readonly newProfileName = signal('');
  readonly addDeviceOpen = signal(false);

  /** Whether the selected device can be removed from the UI: only manual-add
   *  (non-PCPanel/HID) providers like Deej. PCPanel devices are auto-detected
   *  over USB and are never removable here. */
  readonly selectedRemovable = computed(() => {
    const d = this.selected();
    return !!d && !this.isPcPanel(d);
  });

  readonly connState = computed<ConnState>(() => {
    if (this.state.reconnecting()) return 'reconnecting';
    if (!this.state.connected()) return 'connecting';
    return 'connected';
  });

  /** "Show control assignments in UI" setting — gates the device-visual chips. */
  readonly showAssignments = computed(() => this.settings.settings.value()?.mainUIIcons ?? true);

  /** Actual running app version (from the backend), not a hardcoded string. */
  readonly appVersion = computed(() => this.platform.version());

  /** Git branch for local (SNAPSHOT) builds — shown in the footer so running instances are distinguishable. */
  readonly buildBranch = computed(() => this.platform.branch());

  readonly integrationRows = computed<IntegrationRow[]>(() => {
    const s = this.settings.settings.value();
    if (!s) return [];
    const rows: IntegrationRow[] = [];
    // Green "connected" only with positive evidence of a live connection; otherwise
    // an enabled integration is shown neutrally as "enabled" (never a false green).
    if (s.obsEnabled) rows.push(this.row('OBS Studio', this.integrations.obsConnected(), this.integrations.obsScenes.isLoading()));
    if (s.voicemeeterEnabled && this.platform.voicemeeterSupported()) rows.push(this.row('Voicemeeter', false, false)); // no live signal (REST stub)
    if (this.platform.waveLinkSupported() && this.integrations.waveLinkSettings.value()?.enabled) {
      rows.push(this.row('Wave Link', this.integrations.waveLinkConnected(), this.integrations.waveLink.isLoading()));
    }
    if (s.oscEnabled) rows.push(this.row('OSC', this.integrations.oscListening(), this.integrations.oscStatus.isLoading()));
    if (s.mqtt?.enabled) rows.push(this.row('MQTT', this.integrations.mqttConnected(), this.integrations.mqttStatus.isLoading()));
    return rows;
  });

  private row(name: string, connected: boolean, loading: boolean): IntegrationRow {
    if (connected) return { name, dot: 'ok', stateLabel: 'connected', connected: true };
    if (loading) return { name, dot: 'connecting', stateLabel: 'connecting', connected: false };
    return { name, dot: 'idle', stateLabel: 'enabled', connected: false };
  }

  /** Human label for a device row: its descriptor displayName (falls back to a
   *  friendly enum label for older snapshots without a descriptor). */
  deviceTypeLabel(d: DeviceSnapshotDto): string {
    return d.descriptor?.displayName || this.facade.friendlyType(d.deviceType);
  }

  private isPcPanel(d: DeviceSnapshotDto): boolean {
    return (d.descriptor?.providerId ?? 'pcpanel') === 'pcpanel';
  }

  // ── actions ────────────────────────────────────────────────────────────────
  select(serial: string): void { this.facade.select(serial); }

  openAddDevice(): void { this.addDeviceOpen.set(true); }

  removeSelected(): void {
    const d = this.selected();
    if (!d || this.isPcPanel(d)) return;
    this.deviceService.removeDeej(d.serial).subscribe({
      next: () => this.toast.show('Device removed', { kind: 'success' }),
      error: () => this.toast.show('Could not remove device', { kind: 'error' }),
    });
  }

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

  createProfile(): void {
    const serial = this.selectedSerial();
    const name = this.newProfileName().trim();
    this.newProfileOpen.set(false);
    this.newProfileName.set('');
    if (!serial || !name) return;
    this.deviceService.createProfile(serial, name).subscribe({
      next: () => {
        this.state.patchProfiles(serial, ps => ps.includes(name) ? ps : [...ps, name]);
        this.deviceService.switchProfile(serial, name).subscribe({
          error: () => this.toast.show('Profile created, but switching to it failed', { kind: 'error' }),
        });
      },
      error: () => this.toast.show('Could not create profile', { kind: 'error' }),
    });
  }

  onControlClick(e: ControlClick): void {
    const serial = this.selectedSerial();
    if (!serial) return;
    if (e.kind === 'logo') { this.router.navigate(['/lighting', serial]); return; }
    const queryParams: Record<string, string> = {};
    if (e.slot) queryParams['slot'] = e.slot;            // open a specific press/double-press tab
    if (e.profile) queryParams['profile'] = e.profile;   // edit the base-layer profile instead of the active one
    this.router.navigate(['/control', serial, e.index], Object.keys(queryParams).length ? { queryParams } : {});
  }

  openSettings(): void { this.router.navigate(['/settings']); }
}
