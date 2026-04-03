import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DeviceDto, KnobSetting } from '../../models/models';
import { DeviceService } from '../../services/device.service';

@Component({
  selector: 'app-device',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="device-page" *ngIf="device">
      <nav class="breadcrumb">
        <a routerLink="/">Home</a> &rsaquo; {{ device.displayName }}
      </nav>

      <div class="page-header">
        <div>
          <h1 *ngIf="!editingName" (dblclick)="startEditName()">{{ device.displayName }}</h1>
          <div *ngIf="editingName" class="name-edit">
            <input [(ngModel)]="newName" (keyup.enter)="saveName()" (keyup.escape)="cancelEditName()" />
            <button (click)="saveName()" class="btn btn-primary">Save</button>
            <button (click)="cancelEditName()" class="btn btn-secondary">Cancel</button>
          </div>
          <span class="device-type">{{ friendlyType(device.deviceType) }} &mdash; {{ device.serial }}</span>
        </div>
        <div class="header-actions">
          <a [routerLink]="['/lighting', device.serial]" class="btn btn-secondary">Lighting</a>
        </div>
      </div>

      <div class="profiles-section">
        <h2>Profiles</h2>
        <div class="profile-list">
          <button *ngFor="let p of device.profiles"
                  [class.active]="p === device.currentProfile"
                  (click)="switchProfile(p)"
                  class="profile-btn">
            {{ p }}
          </button>
          <button (click)="addProfile()" class="profile-btn add">+ Add Profile</button>
        </div>
      </div>

      <div class="controls-section" *ngIf="device.currentProfile">
        <div class="controls-dials" *ngIf="device.analogCount > 0">
          <h2>Dials ({{ device.analogCount }})</h2>
          <div class="control-grid">
            <div *ngFor="let i of range(device.analogCount)" class="control-card">
              <div class="control-label">Dial {{ i + 1 }}</div>
              <div class="control-assignment" (click)="editDial(i)">
                <span class="assignment-text">{{ getDialLabel(i) }}</span>
                <span class="edit-icon">✎</span>
              </div>
            </div>
          </div>
        </div>

        <div class="controls-buttons" *ngIf="device.buttonCount > 0">
          <h2>Buttons ({{ device.buttonCount }})</h2>
          <div class="control-grid">
            <div *ngFor="let i of range(device.buttonCount)" class="control-card">
              <div class="control-label">Button {{ i + 1 }}</div>
              <div class="control-assignment" (click)="editButton(i)">
                <span class="assignment-text">{{ getButtonLabel(i) }}</span>
                <span class="edit-icon">✎</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div *ngIf="!device.currentProfile" class="no-profile">
        <p>No profile selected. Create one above.</p>
      </div>
    </div>

    <div *ngIf="!device" class="loading">Loading device…</div>
  `,
  styleUrl: './device.component.scss'
})
export class DeviceComponent implements OnInit {
  device: DeviceDto | null = null;
  editingName = false;
  newName = '';
  dialLabels: Map<number, string> = new Map();
  buttonLabels: Map<number, string> = new Map();

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
        this.dialLabels.set(i, this.formatCommands(cmds));
      });
    }
    for (let i = 0; i < buttonCount; i++) {
      this.deviceService.getButtonCommands(serial, currentProfile, i).subscribe(cmds => {
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
    // Full command editor would open a dialog here; for now show a placeholder
    alert(`Editing Dial ${index + 1} — command editor coming soon`);
  }

  editButton(index: number): void {
    alert(`Editing Button ${index + 1} — command editor coming soon`);
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
    return cmds.commands.map((c: any) => c['@class']?.split('.').pop() ?? 'Command').join(', ');
  }
}
