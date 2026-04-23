import { Component, computed, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterModule } from '@angular/router';
import { Location } from '@angular/common';
import { form, FormField, max, min } from '@angular/forms/signals';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DeviceService } from '../../services/device.service';
import { DeviceStateService } from '../../services/device-state.service';
import { ConnectionStatusComponent } from '../../components/connection-status/connection-status.component';
import { LightingConfig } from '../../models/generated/backend.types';

const DEFAULT_LIGHTING: LightingConfig = {
  lightingMode: 'ALL_COLOR',
  individualColors: [],
  volumeBrightnessTrackingEnabled: [],
  allColor: '#ffffff',
  rainbowPhaseShift: 0,
  rainbowBrightness: 255,
  rainbowSpeed: 64,
  rainbowReverse: 0,
  rainbowVertical: 0,
  waveHue: 0,
  waveBrightness: 255,
  waveSpeed: 64,
  waveReverse: 0,
  waveBounce: 0,
  breathHue: 0,
  breathBrightness: 255,
  breathSpeed: 64,
  globalBrightness: 100,

  knobConfigs: [],
  logoConfig: {
    brightness: 0,
    color: '',
    hue: 0,
    mode: 'STATIC',
    speed: 0,
  },
  sliderConfigs: [],
  sliderLabelConfigs: [],
};

@Component({
  selector: 'app-lighting',
  imports: [RouterModule, FormField, MatToolbarModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule, MatSliderModule, MatCardModule, MatProgressSpinnerModule, ConnectionStatusComponent],
  templateUrl: './lighting.component.html',
  styleUrl: './lighting.component.scss',
})
export class LightingComponent {
  private readonly deviceState = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly location = inject(Location);
  private readonly destroyRef = inject(DestroyRef);

  readonly serial = input.required<string>();

  protected readonly saving = signal(false);
  /** True until the device snapshot for this serial has arrived. */
  protected readonly loading = computed(() => this.deviceState.snapshotFor(this.serial)() === null);
  protected readonly configModel = signal<LightingConfig>({...DEFAULT_LIGHTING});
  protected readonly configForm = form(this.configModel, (config) => {
    min(config.globalBrightness, 0);
    max(config.globalBrightness, 100);
    min(config.rainbowPhaseShift, 0);
    max(config.rainbowPhaseShift, 255);
    min(config.rainbowBrightness, 0);
    max(config.rainbowBrightness, 255);
    min(config.rainbowSpeed, 0);
    max(config.rainbowSpeed, 255);
    min(config.breathBrightness, 0);
    max(config.breathBrightness, 255);
    min(config.breathSpeed, 0);
    max(config.breathSpeed, 255);
  });

  constructor() {
    // Seed configModel from DeviceStateService whenever the serial or snapshot changes.
    // lighting_changed WS events from the backend will auto-update the snapshot,
    // so the form also reflects any external changes (e.g. another session's save).
    effect(() => {
      const cfg = this.deviceState.snapshotFor(this.serial)()?.lightingConfig;
      if (cfg && !this.saving()) {
        this.configModel.set(cfg);
      }
    });
  }

  goBack(): void {
    this.location.back();
  }

  save(): void {
    const serial = this.serial();
    if (!serial || this.saving()) return;
    this.saving.set(true);
    // HTTP mutation — backend emits lighting_changed which updates DeviceStateService
    this.deviceService.setLighting(serial, this.configModel())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        complete: () => this.saving.set(false),
        error: () => this.saving.set(false),
      });
  }
}
