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
import { MatDialog } from '@angular/material/dialog';
import { CommandsWrapper, DeviceDto } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { EventService } from '../../services/event.service';
import { CommandConfigComponent, CommandDialogData } from '../../components/command-config/command-config.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    RouterModule, FormsModule,
    MatSidenavModule, MatToolbarModule, MatListModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
  ],
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

  editingDial: number | null = null;
  editingButton: number | null = null;

  private sub?: Subscription;

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
      }
    });
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  private loadDevices(): void {
    this.deviceService.listDevices().subscribe(devices => {
      this.devices = devices;
      if (this.selectedSerial) {
        const found = devices.find(d => d.serial === this.selectedSerial);
        if (found) { this.selectedDevice = found; }
        else if (devices.length > 0) { this.selectDevice(devices[0]); }
        else { this.selectedDevice = null; this.selectedSerial = null; }
      } else if (devices.length > 0) {
        this.selectDevice(devices[0]);
      }
    });
  }

  selectDevice(device: DeviceDto): void {
    this.selectedSerial = device.serial;
    this.selectedDevice = device;
    this.dialLabels.clear(); this.buttonLabels.clear();
    this.dialCommands.clear(); this.buttonCommands.clear();
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

  switchProfile(profileName: string): void {
    if (!this.selectedDevice) return;
    this.deviceService.switchProfile(this.selectedDevice.serial, profileName).subscribe(() => {
      this.selectedDevice!.currentProfile = profileName;
      this.loadAssignments();
    });
  }

  editDial(index: number): void {
    this.editingDial = index;
    this.openCommandDialog('dial', index, this.dialCommands.get(index) ?? null);
  }

  editButton(index: number): void {
    this.editingButton = index;
    this.openCommandDialog('button', index, this.buttonCommands.get(index) ?? null);
  }

  private openCommandDialog(kind: 'dial' | 'button', index: number, currentCommands: CommandsWrapper | null): void {
    const data: CommandDialogData = {
      kind, index, currentCommands,
      profiles: this.selectedDevice?.profiles ?? [],
    };
    const ref = this.dialog.open(CommandConfigComponent, {
      data,
      width: '560px',
      panelClass: 'command-dialog-panel',
    });
    ref.afterClosed().subscribe((result: CommandsWrapper | null | undefined) => {
      this.editingDial = null;
      this.editingButton = null;
      if (result && this.selectedDevice?.currentProfile) {
        const { serial, currentProfile } = this.selectedDevice;
        if (kind === 'dial') {
          this.deviceService.setDialCommands(serial, currentProfile, index, result).subscribe(() => {
            this.dialCommands.set(index, result);
            this.dialLabels.set(index, this.formatCommands(result));
          });
        } else {
          this.deviceService.setButtonCommands(serial, currentProfile, index, result).subscribe(() => {
            this.buttonCommands.set(index, result);
            this.buttonLabels.set(index, this.formatCommands(result));
          });
        }
      }
    });
  }

  getDialLabel(i: number): string { return this.dialLabels.get(i) ?? '—'; }
  getButtonLabel(i: number): string { return this.buttonLabels.get(i) ?? '—'; }
  range(n: number): number[] { return Array.from({ length: n }, (_, i) => i); }

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
    return cmds.commands.map((c: any) => (c['_type'] ?? '').split('.').pop() ?? 'Cmd').join(', ');
  }
}
