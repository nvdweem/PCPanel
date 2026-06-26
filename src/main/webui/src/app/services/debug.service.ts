import { Injectable, signal } from '@angular/core';

/** Empty string means "no override — render the device's real type". */
export type DeviceTypeOverride = '' | 'PCPANEL_MINI' | 'PCPANEL_RGB' | 'PCPANEL_PRO';
/** Empty string means "use the real backend OS". */
export type OsOverride = '' | 'windows' | 'mac' | 'linux';
/** Which onboarding dialog to force open for previewing, or '' for none. */
export type OnboardingPreview = '' | 'new-user' | 'post-install';

const STORAGE_KEY = 'pcpanel.debug.deviceTypeOverride';
const FORCE_NO_DEVICE_KEY = 'pcpanel.debug.forceNoDevice';
const OS_OVERRIDE_KEY = 'pcpanel.debug.osOverride';

/**
 * Frontend-only debug helpers, surfaced on Settings → Debug. They only affect this UI — the backend and
 * the physical device are untouched:
 * <ul>
 *   <li>device-type override — render any connected device as a chosen type;</li>
 *   <li>force the "no device connected" state, to preview it (and its platform help) with a device plugged in;</li>
 *   <li>override which OS the no-device help is shown for;</li>
 *   <li>trigger the first-run / post-install onboarding dialogs on demand.</li>
 * </ul>
 * The first three are persisted to localStorage; the onboarding preview is a transient trigger.
 */
@Injectable({providedIn: 'root'})
export class DebugService {
  readonly deviceTypeOverride = signal<DeviceTypeOverride>(this.readDeviceType());
  readonly forceNoDevice = signal<boolean>(this.readBool(FORCE_NO_DEVICE_KEY));
  readonly osOverride = signal<OsOverride>(this.readOs());
  /** Transient: set to open a dialog for previewing; the dialog clears it when dismissed. */
  readonly onboardingPreview = signal<OnboardingPreview>('');

  setDeviceTypeOverride(value: DeviceTypeOverride): void {
    this.deviceTypeOverride.set(value);
    this.persist(STORAGE_KEY, value);
  }

  setForceNoDevice(value: boolean): void {
    this.forceNoDevice.set(value);
    this.persist(FORCE_NO_DEVICE_KEY, value ? '1' : '');
  }

  setOsOverride(value: OsOverride): void {
    this.osOverride.set(value);
    this.persist(OS_OVERRIDE_KEY, value);
  }

  previewOnboarding(value: OnboardingPreview): void {
    this.onboardingPreview.set(value);
  }

  private persist(key: string, value: string): void {
    try {
      if (value) {
        localStorage.setItem(key, value);
      } else {
        localStorage.removeItem(key);
      }
    } catch {
      // localStorage may be unavailable (private mode); the in-memory signal still works.
    }
  }

  private readDeviceType(): DeviceTypeOverride {
    const stored = this.readRaw(STORAGE_KEY);
    return stored === 'PCPANEL_MINI' || stored === 'PCPANEL_RGB' || stored === 'PCPANEL_PRO' ? stored : '';
  }

  private readOs(): OsOverride {
    const stored = this.readRaw(OS_OVERRIDE_KEY);
    return stored === 'windows' || stored === 'mac' || stored === 'linux' ? stored : '';
  }

  private readBool(key: string): boolean {
    return this.readRaw(key) === '1';
  }

  private readRaw(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }
}
