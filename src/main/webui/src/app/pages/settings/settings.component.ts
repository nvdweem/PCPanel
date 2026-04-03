import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MqttSettings, SettingsDto } from '../../models/models';
import { SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="settings-page" *ngIf="settings">
      <nav class="breadcrumb">
        <a routerLink="/">Home</a> &rsaquo; Settings
      </nav>

      <h1>Settings</h1>

      <div class="tabs">
        <button *ngFor="let t of tabs" [class.active]="activeTab === t" (click)="activeTab = t" class="tab-btn">
          {{ t }}
        </button>
      </div>

      <!-- General -->
      <div *ngIf="activeTab === 'General'" class="tab-content">
        <div class="field-row">
          <label>Show app icons in main UI</label>
          <input type="checkbox" [(ngModel)]="settings.mainUIIcons" />
        </div>
        <div class="field-row">
          <label>Check for updates on startup</label>
          <input type="checkbox" [(ngModel)]="settings.startupVersionCheck" />
        </div>
        <div class="field-row">
          <label>Force volume (Linux)</label>
          <input type="checkbox" [(ngModel)]="settings.forceVolume" />
        </div>
        <div class="field-row">
          <label>Double-click interval (ms)</label>
          <input type="number" [(ngModel)]="settings.dblClickInterval" class="input-num" />
        </div>
        <div class="field-row">
          <label>Prevent single click when double-clicking</label>
          <input type="checkbox" [(ngModel)]="settings.preventClickWhenDblClick" />
        </div>
      </div>

      <!-- OBS -->
      <div *ngIf="activeTab === 'OBS'" class="tab-content">
        <div class="field-row">
          <label>OBS enabled</label>
          <input type="checkbox" [(ngModel)]="settings.obsEnabled" />
        </div>
        <div class="field-row">
          <label>OBS WebSocket host</label>
          <input type="text" [(ngModel)]="settings.obsAddress" class="input-text" />
        </div>
        <div class="field-row">
          <label>OBS WebSocket port</label>
          <input type="text" [(ngModel)]="settings.obsPort" class="input-text" />
        </div>
        <div class="field-row">
          <label>OBS password</label>
          <input type="password" [(ngModel)]="settings.obsPassword" class="input-text" />
        </div>
      </div>

      <!-- VoiceMeeter -->
      <div *ngIf="activeTab === 'VoiceMeeter'" class="tab-content">
        <div class="field-row">
          <label>VoiceMeeter enabled</label>
          <input type="checkbox" [(ngModel)]="settings.voicemeeterEnabled" />
        </div>
        <div class="field-row">
          <label>VoiceMeeter path</label>
          <input type="text" [(ngModel)]="settings.voicemeeterPath" class="input-text" />
        </div>
      </div>

      <!-- Overlay -->
      <div *ngIf="activeTab === 'Overlay'" class="tab-content">
        <div class="field-row">
          <label>Enable overlay</label>
          <input type="checkbox" [(ngModel)]="settings.overlayEnabled" />
        </div>
        <div class="field-row">
          <label>Use logarithmic scale</label>
          <input type="checkbox" [(ngModel)]="settings.overlayUseLog" />
        </div>
        <div class="field-row">
          <label>Show percentage number</label>
          <input type="checkbox" [(ngModel)]="settings.overlayShowNumber" />
        </div>
        <div class="field-row">
          <label>Background color</label>
          <input type="color" [(ngModel)]="settings.overlayBackgroundColor" />
        </div>
        <div class="field-row">
          <label>Text color</label>
          <input type="color" [(ngModel)]="settings.overlayTextColor" />
        </div>
        <div class="field-row">
          <label>Bar color</label>
          <input type="color" [(ngModel)]="settings.overlayBarColor" />
        </div>
        <div class="field-row">
          <label>Position</label>
          <select [(ngModel)]="settings.overlayPosition" class="select">
            <option value="topLeft">Top Left</option>
            <option value="topRight">Top Right</option>
            <option value="bottomLeft">Bottom Left</option>
            <option value="bottomRight">Bottom Right</option>
          </select>
        </div>
      </div>

      <!-- MQTT -->
      <div *ngIf="activeTab === 'MQTT'" class="tab-content">
        <div *ngIf="mqtt">
          <div class="field-row">
            <label>MQTT enabled</label>
            <input type="checkbox" [(ngModel)]="mqtt.enabled" />
          </div>
          <div class="field-row">
            <label>Host</label>
            <input type="text" [(ngModel)]="mqtt.host" class="input-text" />
          </div>
          <div class="field-row">
            <label>Port</label>
            <input type="number" [(ngModel)]="mqtt.port" class="input-num" />
          </div>
          <div class="field-row">
            <label>Username</label>
            <input type="text" [(ngModel)]="mqtt.username" class="input-text" />
          </div>
          <div class="field-row">
            <label>Password</label>
            <input type="password" [(ngModel)]="mqtt.password" class="input-text" />
          </div>
          <div class="field-row">
            <label>Base topic</label>
            <input type="text" [(ngModel)]="mqtt.baseTopic" class="input-text" />
          </div>
          <div class="field-row">
            <label>Home Assistant integration</label>
            <input type="checkbox" [(ngModel)]="mqtt.homeAssistant" />
          </div>
        </div>
      </div>

      <div class="actions">
        <button (click)="save()" class="btn btn-primary">Save Settings</button>
        <a routerLink="/" class="btn btn-secondary">Cancel</a>
      </div>
    </div>

    <div *ngIf="!settings" class="loading">Loading settings…</div>
  `,
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {
  settings: SettingsDto | null = null;
  mqtt: MqttSettings | null = null;
  activeTab = 'General';
  tabs = ['General', 'OBS', 'VoiceMeeter', 'Overlay', 'MQTT'];

  constructor(private settingsService: SettingsService) {}

  ngOnInit(): void {
    this.settingsService.getSettings().subscribe(s => this.settings = s);
    this.settingsService.getMqttSettings().subscribe(m => this.mqtt = m);
  }

  save(): void {
    if (!this.settings) return;
    this.settingsService.updateSettings(this.settings).subscribe(() => {
      if (this.mqtt) {
        this.settingsService.updateMqttSettings(this.mqtt).subscribe(() => {
          alert('Settings saved!');
        });
      } else {
        alert('Settings saved!');
      }
    });
  }
}
