import { Component, OnInit, OnDestroy } from '@angular/core';
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
import { NgIf } from '@angular/common';
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
    RouterModule, FormsModule, NgIf,
    MatSidenavModule, MatToolbarModule, MatListModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatSliderModule, MatTooltipModule,
    PcpanelProComponent, PcpanelMiniComponent, PcpanelRgbComponent,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit, OnDestroy {
  devices: DeviceDto[] = [];
  selectedSerial: string | null = null;
  selectedDevice: DeviceDto | null = null;
  lightingConfig: LightingConfig | null = null;

  dialLabels: Map<number, string> = new Map();
  dialCommands: Map<number, CommandsWrapper> = new Map();
  /** Live 0-255 hardware values received from WebSocket */
  analogValues: Map<number, number> = new Map();
  activeDial: number | null = null;

  private sub?: Subscription;
  private brightnessTimer?: ReturnType<typeof setTimeout>;

  constructor(
    private deviceService: DeviceService,
    private eventService: EventService,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.loadDevices();
    this.sub = this.eventService.events$.subscribe(event => {
      if (event.type === 'device_connected' || event.type === 'device_disconnected') {
        this.loadDevices();
      } else if (event.type === 'knob_rotate' && event.serial === this.selectedSerial) {
        const e = event as any;
        // WS sends 0-255 raw hardware values
        this.analogValues = new Map(this.analogValues).set(e.knob, e.value);
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    if (this.brightnessTimer) clearTimeout(this.brightnessTimer);
  }

  private loadDevices(): void {
    this.deviceService.listDevices().subscribe(devices => {
      this.devices = devices;
      if (this.selectedSerial) {
        const found = devices.find(d => d.serial === this.selectedSerial);
        if (found) { this.selectedDevice = found; }
        else if (devices.length > 0) { this.selectDevice(devices[0]); }
        else { this.selectedDevice = null; this.selectedSerial = null; this.lightingConfig = null; }
      } else if (devices.length > 0) {
        this.selectDevice(devices[0]);
      }
    });
  }

  selectDevice(device: DeviceDto): void {
    this.selectedSerial = device.serial;
    this.selectedDevice = device;
    this.dialLabels = new Map();
    this.dialCommands = new Map();
    this.analogValues = new Map();
    this.lightingConfig = null;
    this.loadAssignments();
    this.loadLighting(device.serial);
  }

  private loadAssignments(): void {
    if (!this.selectedDevice?.currentProfile) return;
    const { serial, currentProfile, analogCount } = this.selectedDevice;
    for (let i = 0; i < analogCount; i++) {
      this.deviceService.getDialCommands(serial, currentProfile, i).subscribe(cmds => {
        this.dialCommands.set(i, cmds);
        this.dialLabels = new Map(this.dialLabels).set(i, this.formatCommands(cmds));
      });
    }
  }

  private loadLighting(serial: string): void {
    this.deviceService.getLighting(serial).subscribe(cfg => { this.lightingConfig = cfg; });
  }

  switchProfile(profileName: string): void {
    if (!this.selectedDevice) return;
    this.deviceService.switchProfile(this.selectedDevice.serial, profileName).subscribe(() => {
      this.selectedDevice!.currentProfile = profileName;
      this.loadAssignments();
    });
  }

  onBrightnessChange(value: number): void {
    if (!this.lightingConfig || !this.selectedDevice) return;
    this.lightingConfig = { ...this.lightingConfig, globalBrightness: value };
    if (this.brightnessTimer) clearTimeout(this.brightnessTimer);
    this.brightnessTimer = setTimeout(() => {
      if (this.selectedDevice && this.lightingConfig) {
        this.deviceService.setLighting(this.selectedDevice.serial, this.lightingConfig).subscribe();
      }
    }, 200);
  }

  onDialClick(index: number): void {
    this.activeDial = index;
    const data: CommandDialogData = {
      kind: 'dial', index,
      currentCommands: this.dialCommands.get(index) ?? null,
      profiles: this.selectedDevice?.profiles ?? [],
    };
    const ref = this.dialog.open(CommandConfigComponent, { data, width: '560px' });
    ref.afterClosed().subscribe((result: CommandsWrapper | null | undefined) => {
      this.activeDial = null;
      if (result && this.selectedDevice?.currentProfile) {
        const { serial, currentProfile } = this.selectedDevice;
        this.deviceService.setDialCommands(serial, currentProfile, index, result).subscribe(() => {
          this.dialCommands.set(index, result);
          this.dialLabels = new Map(this.dialLabels).set(index, this.formatCommands(result));
        });
      }
    });
  }

  get isPro(): boolean { return this.selectedDevice?.deviceType === 'PCPANEL_PRO'; }
  get isMini(): boolean { return this.selectedDevice?.deviceType === 'PCPANEL_MINI'; }
  get isRgb(): boolean { return this.selectedDevice?.deviceType === 'PCPANEL_RGB'; }

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
