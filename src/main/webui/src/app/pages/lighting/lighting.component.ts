import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LightingConfig } from '../../models/models';
import { DeviceService } from '../../services/device.service';

@Component({
  selector: 'app-lighting',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="lighting-page" *ngIf="config">
      <nav class="breadcrumb">
        <a routerLink="/">Home</a> &rsaquo;
        <a [routerLink]="['/device', serial]">Device</a> &rsaquo;
        Lighting
      </nav>

      <h1>Lighting Configuration</h1>

      <div class="section">
        <label>Mode</label>
        <select [(ngModel)]="config.lightingMode" class="select">
          <option value="ALL_COLOR">All Color</option>
          <option value="ALL_RAINBOW">Rainbow</option>
          <option value="ALL_WAVE">Wave</option>
          <option value="ALL_BREATH">Breath</option>
          <option value="SINGLE_COLOR">Per-control Colors</option>
          <option value="CUSTOM">Custom</option>
        </select>
      </div>

      <div class="section" *ngIf="config.lightingMode === 'ALL_COLOR'">
        <label>Color</label>
        <input type="color" [(ngModel)]="config.allColor" class="color-picker" />
        <span class="color-hex">{{ config.allColor }}</span>
      </div>

      <div class="section rainbow-section" *ngIf="config.lightingMode === 'ALL_RAINBOW'">
        <div class="field">
          <label>Phase Shift</label>
          <input type="range" min="0" max="255" [(ngModel)]="config.rainbowPhaseShift" />
          <span>{{ config.rainbowPhaseShift }}</span>
        </div>
        <div class="field">
          <label>Brightness</label>
          <input type="range" min="0" max="255" [(ngModel)]="config.rainbowBrightness" />
          <span>{{ config.rainbowBrightness }}</span>
        </div>
        <div class="field">
          <label>Speed</label>
          <input type="range" min="0" max="255" [(ngModel)]="config.rainbowSpeed" />
          <span>{{ config.rainbowSpeed }}</span>
        </div>
      </div>

      <div class="section" *ngIf="config.lightingMode === 'ALL_BREATH'">
        <div class="field">
          <label>Brightness</label>
          <input type="range" min="0" max="255" [(ngModel)]="config.breathBrightness" />
          <span>{{ config.breathBrightness }}</span>
        </div>
        <div class="field">
          <label>Speed</label>
          <input type="range" min="0" max="255" [(ngModel)]="config.breathSpeed" />
          <span>{{ config.breathSpeed }}</span>
        </div>
      </div>

      <div class="section">
        <label>Global Brightness</label>
        <div class="field">
          <input type="range" min="0" max="100" [(ngModel)]="config.globalBrightness" />
          <span>{{ config.globalBrightness }}%</span>
        </div>
      </div>

      <div class="actions">
        <button (click)="save()" class="btn btn-primary">Save Lighting</button>
        <a [routerLink]="['/device', serial]" class="btn btn-secondary">Cancel</a>
      </div>
    </div>

    <div *ngIf="!config" class="loading">Loading…</div>
  `,
  styleUrl: './lighting.component.scss'
})
export class LightingComponent implements OnInit {
  serial = '';
  config: LightingConfig | null = null;

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService
  ) {}

  ngOnInit(): void {
    this.serial = this.route.snapshot.paramMap.get('serial')!;
    this.deviceService.getLighting(this.serial).subscribe(c => this.config = c);
  }

  save(): void {
    if (!this.config) return;
    this.deviceService.setLighting(this.serial, this.config).subscribe(() => {
      alert('Lighting saved!');
    });
  }
}
