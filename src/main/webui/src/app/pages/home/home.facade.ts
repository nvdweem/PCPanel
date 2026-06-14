import { computed, DestroyRef, effect, inject, Injectable, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DeviceService } from '../../services/device.service';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceSnapshotDto, LightingConfig } from '../../models/generated/backend.types';

@Injectable()
export class HomeFacade implements OnDestroy {
  readonly deviceState = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly destroyRef = inject(DestroyRef);

  readonly selectedSerial = signal<string | null>(null);
  private readonly _pendingBrightness = signal<number | null>(null);
  private brightnessTimer?: ReturnType<typeof setTimeout>;

  /** The selected device's full snapshot (superset of DeviceDto). */
  readonly selectedDevice = computed<DeviceSnapshotDto | null>(() => {
    return this.deviceState.snapshotFor(this.selectedSerial)();
  });

  /** Lighting config with in-flight brightness drag applied optimistically. */
  readonly lightingConfig = computed<LightingConfig | null>(() => {
    const base = this.selectedDevice()?.lightingConfig ?? null;
    if (!base) return null;
    const pending = this._pendingBrightness();
    return pending !== null ? {...base, globalBrightness: pending} : base;
  });

  readonly isPro = computed(() => this.selectedDevice()?.deviceType === 'PCPANEL_PRO');
  readonly isMini = computed(() => this.selectedDevice()?.deviceType === 'PCPANEL_MINI');
  readonly isRgb = computed(() => this.selectedDevice()?.deviceType === 'PCPANEL_RGB');

  constructor() {
    // Auto-select the first device once state arrives and nothing is selected.
    effect(() => {
      const devices = this.deviceState.devices();
      if (!this.selectedSerial() && this.deviceState.deviceCount() > 0) {
        this.selectedSerial.set(Object.keys(this.deviceState.devices())[0]);
      }
    });

    // If the selected device disconnects, fall back to the first remaining device.
    effect(() => {
      const serial = this.selectedSerial();
      const devices = this.deviceState.devices();
      if (serial && !devices[serial]) {
        const first = Object.values(devices)[0];
        this.selectedSerial.set(first ? first.serial : null);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.brightnessTimer) clearTimeout(this.brightnessTimer);
  }

  selectDevice(device: DeviceSnapshotDto): void {
    this.selectedSerial.set(device.serial);
  }

  switchProfile(profileName: string): void {
    const dev = this.selectedDevice();
    if (!dev) return;
    // HTTP mutation — backend will emit profile_switched WS event which updates DeviceStateService
    this.deviceService.switchProfile(dev.serial, profileName)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe();
  }

  onBrightnessChange(value: number): void {
    const dev = this.selectedDevice();
    if (!dev) return;

    // Optimistically show dragged value while debouncing
    this._pendingBrightness.set(value);

    if (this.brightnessTimer) clearTimeout(this.brightnessTimer);
    this.brightnessTimer = setTimeout(() => {
      const cfg = this.selectedDevice()?.lightingConfig;
      if (cfg) {
        const updated = {...cfg, globalBrightness: value};
        // HTTP mutation — backend will emit lighting_changed WS event
        this.deviceService.setLighting(dev.serial, updated)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => this._pendingBrightness.set(null));
      } else {
        this._pendingBrightness.set(null);
      }
    }, 200);
  }

  friendlyType(type: string): string {
    switch (type) {
      case 'PCPANEL_RGB':
        return 'PCPanel RGB';
      case 'PCPANEL_MINI':
        return 'PCPanel Mini';
      case 'PCPANEL_PRO':
        return 'PCPanel Pro';
      default:
        return type;
    }
  }
}
