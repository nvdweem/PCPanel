import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { DeviceDto, WsEvent } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { EventService } from '../../services/event.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="home-page">
      <header class="app-header">
        <h1>PCPanel Controller</h1>
        <a routerLink="/settings" class="btn btn-icon" title="Settings">⚙</a>
      </header>

      <main>
        <div *ngIf="devices.length === 0" class="no-devices">
          <p>No devices connected.</p>
          <p class="hint">Connect a PCPanel device via USB to get started.</p>
        </div>

        <div class="device-grid">
          <div *ngFor="let device of devices" class="device-card">
            <div class="device-header">
              <div class="device-info">
                <h2>{{ device.displayName }}</h2>
                <span class="device-type">{{ friendlyType(device.deviceType) }}</span>
              </div>
              <a [routerLink]="['/device', device.serial]" class="btn btn-primary">Configure</a>
            </div>

            <div class="profile-row">
              <label>Profile:</label>
              <select (change)="switchProfile(device, $event)" class="profile-select">
                <option *ngFor="let p of device.profiles" [value]="p" [selected]="p === device.currentProfile">
                  {{ p }}
                </option>
              </select>
            </div>

            <div class="device-actions">
              <a [routerLink]="['/lighting', device.serial]" class="btn btn-secondary">Lighting</a>
            </div>
          </div>
        </div>
      </main>
    </div>
  `,
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit, OnDestroy {
  devices: DeviceDto[] = [];
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
    this.deviceService.listDevices().subscribe(devices => this.devices = devices);
  }

  switchProfile(device: DeviceDto, event: Event): void {
    const profileName = (event.target as HTMLSelectElement).value;
    this.deviceService.switchProfile(device.serial, profileName).subscribe(() => {
      device.currentProfile = profileName;
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
}
