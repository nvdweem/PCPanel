import { Injectable, signal } from '@angular/core';

/** Empty string means "no override — render the device's real type". */
export type DeviceTypeOverride = '' | 'PCPANEL_MINI' | 'PCPANEL_RGB' | 'PCPANEL_PRO';

const STORAGE_KEY = 'pcpanel.debug.deviceTypeOverride';

/**
 * Frontend-only debug helpers. Currently exposes a device-type override that
 * forces every connected device to be rendered as a chosen type, so a single
 * connected PCPanel Pro (which has the most controls) can stand in for the
 * Mini/RGB visuals during development. Persisted to localStorage so it survives
 * reloads. This only affects rendering/editing in the UI — the backend and the
 * physical device are untouched.
 */
@Injectable({providedIn: 'root'})
export class DebugService {
  readonly deviceTypeOverride = signal<DeviceTypeOverride>(this.read());

  setDeviceTypeOverride(value: DeviceTypeOverride): void {
    this.deviceTypeOverride.set(value);
    try {
      if (value) {
        localStorage.setItem(STORAGE_KEY, value);
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // localStorage may be unavailable (private mode); the in-memory signal still works.
    }
  }

  private read(): DeviceTypeOverride {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'PCPANEL_MINI' || stored === 'PCPANEL_RGB' || stored === 'PCPANEL_PRO') {
        return stored;
      }
    } catch {
      // ignore
    }
    return '';
  }
}
