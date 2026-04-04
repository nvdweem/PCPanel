import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { CommandsWrapper, DeviceDto, LightingConfig } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { EventService } from '../../services/event.service';
import { CommandConfigComponent, CommandDialogData } from '../../components/command-config/command-config.component';
import { PcpanelProComponent } from '../../components/device-visual/pcpanel-pro.component';
import { PcpanelMiniComponent } from '../../components/device-visual/pcpanel-mini.component';
import { PcpanelRgbComponent } from '../../components/device-visual/pcpanel-rgb.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    RouterModule, FormsModule,
    MatSidenavModule, MatToolbarModule, MatListModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatSliderModule, MatTooltipModule,
    PcpanelProComponent, PcpanelMiniComponent, PcpanelRgbComponent,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit, OnDestroy {
  private deviceService = inject(DeviceService);
  private eventService = inject(EventService);
  private dialog = inject(MatDialog);

  devices = signal<DeviceDto[]>([]);
  selectedSerial = signal<string | null>(null);
  selectedDevice = signal<DeviceDto | null>(null);
  lightingConfig = signal<LightingConfig | null>(null);
  dialLabels = signal<Map<number, string>>(new Map());
  dialCommands = signal<Map<number, CommandsWrapper>>(new Map());
  /** Live 0-255 hardware values received from WebSocket */
  analogValues = signal<Map<number, number>>(new Map());
  activeDial = signal<number | null>(null);

  isPro = computed(() => this.selectedDevice()?.deviceType === 'PCPANEL_PRO');
  isMini = computed(() => this.selectedDevice()?.deviceType === 'PCPANEL_MINI');
  isRgb = computed(() => this.selectedDevice()?.deviceType === 'PCPANEL_RGB');

  private sub?: Subscription;
  private brightnessTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.loadDevices();
    this.sub = this.eventService.events$.subscribe(event => {
      if (event.type === 'device_connected' || event.type === 'device_disconnected') {
        this.loadDevices();
      } else if (event.type === 'knob_rotate' && event.serial === this.selectedSerial()) {
        const e = event as any;
        this.analogValues.set(new Map(this.analogValues()).set(e.knob, e.value));
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.brightnessTimer) clearTimeout(this.brightnessTimer);
  }

  private loadDevices(): void {
    this.deviceService.listDevices().subscribe(devices => {
      this.devices.set(devices);
      const serial = this.selectedSerial();
      if (serial) {
        const found = devices.find(d => d.serial === serial);
        if (found) { this.selectedDevice.set(found); }
        else if (devices.length > 0) { this.selectDevice(devices[0]); }
        else { this.selectedDevice.set(null); this.selectedSerial.set(null); this.lightingConfig.set(null); }
      } else if (devices.length > 0) {
        this.selectDevice(devices[0]);
      }
    });
  }

  selectDevice(device: DeviceDto): void {
    this.selectedSerial.set(device.serial);
    this.selectedDevice.set(device);
    this.dialLabels.set(new Map());
    this.dialCommands.set(new Map());
    this.analogValues.set(new Map());
    this.lightingConfig.set(null);
    this.loadAssignments();
    this.loadLighting(device.serial);
  }

  private loadAssignments(): void {
    const dev = this.selectedDevice();
    if (!dev?.currentProfile) return;
    const { serial, currentProfile, analogCount } = dev;
    for (let i = 0; i < analogCount; i++) {
      this.deviceService.getDialCommands(serial, currentProfile, i).subscribe(cmds => {
        const updated = new Map(this.dialCommands()).set(i, cmds);
        this.dialCommands.set(updated);
        this.dialLabels.set(new Map(this.dialLabels()).set(i, this.formatCommands(cmds)));
      });
    }
  }

  private loadLighting(serial: string): void {
    this.deviceService.getLighting(serial).subscribe(cfg => this.lightingConfig.set(cfg));
  }

  switchProfile(profileName: string): void {
    const dev = this.selectedDevice();
    if (!dev) return;
    this.deviceService.switchProfile(dev.serial, profileName).subscribe(() => {
      this.selectedDevice.set({ ...dev, currentProfile: profileName });
      this.loadAssignments();
    });
  }

  onBrightnessChange(value: number): void {
    const cfg = this.lightingConfig();
    const dev = this.selectedDevice();
    if (!cfg || !dev) return;
    const updated = { ...cfg, globalBrightness: value };
    this.lightingConfig.set(updated);
    if (this.brightnessTimer) clearTimeout(this.brightnessTimer);
    this.brightnessTimer = setTimeout(() => {
      const latestCfg = this.lightingConfig();
      const latestDev = this.selectedDevice();
      if (latestDev && latestCfg) {
        this.deviceService.setLighting(latestDev.serial, latestCfg).subscribe();
      }
    }, 200);
  }

  onDialClick(index: number): void {
    this.activeDial.set(index);
    const data: CommandDialogData = {
      kind: 'dial', index,
      currentCommands: this.dialCommands().get(index) ?? null,
      profiles: this.selectedDevice()?.profiles ?? [],
    };
    const ref = this.dialog.open(CommandConfigComponent, { data, width: '560px' });
    ref.afterClosed().subscribe((result: CommandsWrapper | null | undefined) => {
      this.activeDial.set(null);
      const dev = this.selectedDevice();
      if (result && dev?.currentProfile) {
        const { serial, currentProfile } = dev;
        this.deviceService.setDialCommands(serial, currentProfile, index, result).subscribe(() => {
          this.dialCommands.set(new Map(this.dialCommands()).set(index, result));
          this.dialLabels.set(new Map(this.dialLabels()).set(index, this.formatCommands(result)));
        });
      }
    });
  }

  friendlyType(type: string): string {
    switch (type) {
      case 'PCPANEL_RGB': return 'PCPanel RGB';
      case 'PCPANEL_MINI': return 'PCPanel Mini';
      case 'PCPANEL_PRO': return 'PCPanel Pro';
      default: return type;
    }
  }

  private formatCommands(cmds: any): string {
    if (!cmds?.commands?.length) return '—';
    return cmds.commands.map((c: any) => (c['_type'] ?? '').split('.').pop() ?? '?').join(', ');
  }
}
