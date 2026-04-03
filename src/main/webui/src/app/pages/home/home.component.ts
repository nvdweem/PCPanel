import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { CommandsWrapper, DeviceDto } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { EventService } from '../../services/event.service';
import { CommandConfigComponent } from '../../components/command-config/command-config.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterModule, FormsModule, CommandConfigComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, OnDestroy {
  devices: DeviceDto[] = [];
  selectedSerial: string | null = null;
  selectedDevice: DeviceDto | null = null;

  dialLabels: Map<number, string> = new Map();
  buttonLabels: Map<number, string> = new Map();
  dialCommands: Map<number, CommandsWrapper> = new Map();
  buttonCommands: Map<number, CommandsWrapper> = new Map();

  editingCommand = false;
  editCommandKind: 'dial' | 'button' = 'button';
  editCommandIndex = 0;
  editCurrentCommands: CommandsWrapper | null = null;
  editingDial: number | null = null;
  editingButton: number | null = null;

  private sub?: Subscription;

  constructor(
    private deviceService: DeviceService,
    private eventService: EventService
  ) {}

  ngOnInit(): void {
    this.loadDevices();
    this.sub = this.eventService.events$.subscribe(event => {
      if (event.type === 'device_connected' || event.type === 'device_disconnected') {
        this.loadDevices();
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private loadDevices(): void {
    this.deviceService.listDevices().subscribe(devices => {
      this.devices = devices;
      if (this.selectedSerial) {
        const found = devices.find(d => d.serial === this.selectedSerial);
        if (found) {
          this.selectedDevice = found;
        } else if (devices.length > 0) {
          this.selectDevice(devices[0]);
        } else {
          this.selectedDevice = null;
          this.selectedSerial = null;
        }
      } else if (devices.length > 0) {
        this.selectDevice(devices[0]);
      }
    });
  }

  selectDevice(device: DeviceDto): void {
    this.selectedSerial = device.serial;
    this.selectedDevice = device;
    this.dialLabels.clear();
    this.buttonLabels.clear();
    this.dialCommands.clear();
    this.buttonCommands.clear();
    this.loadAssignments();
  }

  private loadAssignments(): void {
    if (!this.selectedDevice?.currentProfile) return;
    const { serial, currentProfile, analogCount, buttonCount } = this.selectedDevice;
    for (let i = 0; i < analogCount; i++) {
      this.deviceService.getDialCommands(serial, currentProfile, i).subscribe(cmds => {
        this.dialCommands.set(i, cmds);
        this.dialLabels.set(i, this.formatCommands(cmds));
      });
    }
    for (let i = 0; i < buttonCount; i++) {
      this.deviceService.getButtonCommands(serial, currentProfile, i).subscribe(cmds => {
        this.buttonCommands.set(i, cmds);
        this.buttonLabels.set(i, this.formatCommands(cmds));
      });
    }
  }

  switchProfile(device: DeviceDto, event: Event): void {
    const profileName = (event.target as HTMLSelectElement).value;
    this.deviceService.switchProfile(device.serial, profileName).subscribe(() => {
      device.currentProfile = profileName;
      if (device === this.selectedDevice) this.loadAssignments();
    });
  }

  editDial(index: number): void {
    this.editingDial = index;
    this.editingButton = null;
    this.editCommandKind = 'dial';
    this.editCommandIndex = index;
    this.editCurrentCommands = this.dialCommands.get(index) ?? null;
    this.editingCommand = true;
  }

  editButton(index: number): void {
    this.editingButton = index;
    this.editingDial = null;
    this.editCommandKind = 'button';
    this.editCommandIndex = index;
    this.editCurrentCommands = this.buttonCommands.get(index) ?? null;
    this.editingCommand = true;
  }

  onCommandSaved(wrapper: CommandsWrapper): void {
    if (!this.selectedDevice?.currentProfile) return;
    const { serial, currentProfile } = this.selectedDevice;
    const idx = this.editCommandIndex;
    if (this.editCommandKind === 'dial') {
      this.deviceService.setDialCommands(serial, currentProfile, idx, wrapper).subscribe(() => {
        this.dialCommands.set(idx, wrapper);
        this.dialLabels.set(idx, this.formatCommands(wrapper));
        this.editingCommand = false;
        this.editingDial = null;
      });
    } else {
      this.deviceService.setButtonCommands(serial, currentProfile, idx, wrapper).subscribe(() => {
        this.buttonCommands.set(idx, wrapper);
        this.buttonLabels.set(idx, this.formatCommands(wrapper));
        this.editingCommand = false;
        this.editingButton = null;
      });
    }
  }

  closeCommandEditor(): void {
    this.editingCommand = false;
    this.editingDial = null;
    this.editingButton = null;
  }

  getDialLabel(i: number): string {
    return this.dialLabels.get(i) ?? '—';
  }

  getButtonLabel(i: number): string {
    return this.buttonLabels.get(i) ?? '—';
  }

  range(n: number): number[] {
    return Array.from({ length: n }, (_, i) => i);
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
    return cmds.commands.map((c: any) => {
      const t: string = c['_type'] ?? '';
      return t.split('.').pop() ?? 'Cmd';
    }).join(', ');
  }
}
