import { Component, OnInit } from '@angular/core';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { CommandsWrapper, DeviceDto } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { CommandConfigComponent, CommandDialogData } from '../../components/command-config/command-config.component';

@Component({
  selector: 'app-device',
  standalone: true,
  imports: [
    RouterModule, FormsModule,
    MatToolbarModule, MatButtonModule, MatButtonToggleModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatProgressSpinnerModule,
  ],
  templateUrl: './device.component.html',
  styleUrl: './device.component.scss'
})
export class DeviceComponent implements OnInit {
  device: DeviceDto | null = null;
  editingName = false;
  newName = '';
  dialLabels: Map<number, string> = new Map();
  buttonLabels: Map<number, string> = new Map();
  dialCommands: Map<number, CommandsWrapper> = new Map();
  buttonCommands: Map<number, CommandsWrapper> = new Map();

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService,
    private dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    const serial = this.route.snapshot.paramMap.get('serial')!;
    this.loadDevice(serial);
  }

  private loadDevice(serial: string): void {
    this.deviceService.getDevice(serial).subscribe(d => {
      this.device = d;
      if (d.currentProfile) this.loadAssignments();
    });
  }

  private loadAssignments(): void {
    if (!this.device?.currentProfile) return;
    const { serial, currentProfile, analogCount, buttonCount } = this.device;
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

  startEditName(): void { this.editingName = true; this.newName = this.device!.displayName; }

  saveName(): void {
    if (!this.device || !this.newName.trim()) return;
    this.deviceService.renameDevice(this.device.serial, this.newName.trim()).subscribe(() => {
      this.device!.displayName = this.newName.trim();
      this.editingName = false;
    });
  }

  cancelEditName(): void { this.editingName = false; }

  switchProfile(name: string): void {
    if (!this.device) return;
    this.deviceService.switchProfile(this.device.serial, name).subscribe(() => {
      this.device!.currentProfile = name;
      this.loadAssignments();
    });
  }

  addProfile(): void {
    const name = prompt('Profile name:');
    if (!name || !this.device) return;
    this.deviceService.createProfile(this.device.serial, name).subscribe(p => {
      this.device!.profiles.push(p.name);
      this.switchProfile(p.name);
    });
  }

  editDial(index: number): void { this.openDialog('dial', index, this.dialCommands.get(index) ?? null); }
  editButton(index: number): void { this.openDialog('button', index, this.buttonCommands.get(index) ?? null); }

  private openDialog(kind: 'dial' | 'button', index: number, currentCommands: CommandsWrapper | null): void {
    const data: CommandDialogData = { kind, index, currentCommands, profiles: this.device?.profiles ?? [] };
    const ref = this.dialog.open(CommandConfigComponent, { data, width: '560px', panelClass: 'command-dialog-panel' });
    ref.afterClosed().subscribe((result: CommandsWrapper | null | undefined) => {
      if (!result || !this.device?.currentProfile) return;
      const { serial, currentProfile } = this.device;
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
    });
  }

  getDialLabel(i: number): string { return this.dialLabels.get(i) ?? '— unassigned —'; }
  getButtonLabel(i: number): string { return this.buttonLabels.get(i) ?? '— unassigned —'; }
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
    if (!cmds?.commands?.length) return '— unassigned —';
    return cmds.commands.map((c: any) => (c['_type'] ?? '').split('.').pop() ?? 'Command').join(', ');
  }
}
