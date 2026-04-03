import { Component, OnInit } from '@angular/core';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommandsWrapper, DeviceDto, KnobSetting } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { CommandConfigComponent } from '../../components/command-config/command-config.component';

@Component({
  selector: 'app-device',
  standalone: true,
  imports: [RouterModule, FormsModule, CommandConfigComponent],
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

  // Command editor modal state
  editingCommand = false;
  editCommandKind: 'dial' | 'button' = 'button';
  editCommandIndex = 0;
  editCurrentCommands: CommandsWrapper | null = null;

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService
  ) {}

  ngOnInit(): void {
    const serial = this.route.snapshot.paramMap.get('serial')!;
    this.loadDevice(serial);
  }

  private loadDevice(serial: string): void {
    this.deviceService.getDevice(serial).subscribe(d => {
      this.device = d;
      if (d.currentProfile) {
        this.loadAssignments();
      }
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

  startEditName(): void {
    this.editingName = true;
    this.newName = this.device!.displayName;
  }

  saveName(): void {
    if (!this.device || !this.newName.trim()) return;
    this.deviceService.renameDevice(this.device.serial, this.newName.trim()).subscribe(() => {
      this.device!.displayName = this.newName.trim();
      this.editingName = false;
    });
  }

  cancelEditName(): void {
    this.editingName = false;
  }

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

  editDial(index: number): void {
    this.editCommandKind = 'dial';
    this.editCommandIndex = index;
    this.editCurrentCommands = this.dialCommands.get(index) ?? null;
    this.editingCommand = true;
  }

  editButton(index: number): void {
    this.editCommandKind = 'button';
    this.editCommandIndex = index;
    this.editCurrentCommands = this.buttonCommands.get(index) ?? null;
    this.editingCommand = true;
  }

  onCommandSaved(wrapper: CommandsWrapper): void {
    if (!this.device?.currentProfile) return;
    const { serial, currentProfile } = this.device;
    const idx = this.editCommandIndex;

    if (this.editCommandKind === 'dial') {
      this.deviceService.setDialCommands(serial, currentProfile, idx, wrapper).subscribe(() => {
        this.dialCommands.set(idx, wrapper);
        this.dialLabels.set(idx, this.formatCommands(wrapper));
        this.editingCommand = false;
      });
    } else {
      this.deviceService.setButtonCommands(serial, currentProfile, idx, wrapper).subscribe(() => {
        this.buttonCommands.set(idx, wrapper);
        this.buttonLabels.set(idx, this.formatCommands(wrapper));
        this.editingCommand = false;
      });
    }
  }

  closeCommandEditor(): void {
    this.editingCommand = false;
  }

  getDialLabel(i: number): string {
    return this.dialLabels.get(i) ?? '— unassigned —';
  }

  getButtonLabel(i: number): string {
    return this.buttonLabels.get(i) ?? '— unassigned —';
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
    if (!cmds || !cmds.commands || cmds.commands.length === 0) return '— unassigned —';
    return cmds.commands.map((c: any) => {
      const t: string = c['_type'] ?? '';
      return t.split('.').pop() ?? 'Command';
    }).join(', ');
  }
}
