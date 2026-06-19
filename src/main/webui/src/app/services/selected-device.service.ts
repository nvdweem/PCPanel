import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { DeviceStateService } from './device-state.service';
import { DeviceSnapshotDto } from '../models/generated/backend.types';

/** Tracks the currently-selected device on the Home screen and exposes
 *  convenience views over the live device map. */
@Injectable({ providedIn: 'root' })
export class SelectedDeviceService {
  private readonly state = inject(DeviceStateService);

  private readonly _selected = signal<string | null>(null);
  readonly selectedSerial = this._selected.asReadonly();

  readonly devices = computed<DeviceSnapshotDto[]>(() => Object.values(this.state.devices()));
  readonly hasDevices = computed(() => this.devices().length > 0);

  readonly selected = computed<DeviceSnapshotDto | null>(() => {
    const s = this._selected();
    return s ? this.state.devices()[s] ?? null : null;
  });

  constructor() {
    // Auto-select the first device, and recover if the selected one disappears.
    effect(() => {
      const devices = this.devices();
      const current = this._selected();
      if (devices.length === 0) {
        if (current !== null) this._selected.set(null);
        return;
      }
      if (!current || !devices.some(d => d.serial === current)) {
        this._selected.set(devices[0].serial);
      }
    });
  }

  select(serial: string): void {
    this._selected.set(serial);
  }

  friendlyType(type: string): string {
    switch (type) {
      case 'PCPANEL_PRO': return 'PCPanel Pro';
      case 'PCPANEL_MINI': return 'PCPanel Mini';
      case 'PCPANEL_RGB': return 'PCPanel RGB';
      default: return type;
    }
  }
}
