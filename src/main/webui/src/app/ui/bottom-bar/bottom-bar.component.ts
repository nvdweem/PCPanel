import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import { DeviceCapabilitiesService } from '../../services/device-capabilities.service';
import { IconComponent } from '../icon/icon.component';
import { SliderComponent } from '../slider/slider.component';
import { SelectComponent, SelectOption } from '../select/select.component';
import { ToastService } from '../toast/toast.service';

/**
 * Shared bottom bar for the device-scoped screens (Home / Lighting / Advanced): global brightness,
 * an optional profile selector, and contextual navigation. The profile selector is only shown on
 * the main (Home) screen; the nav buttons hide the page you're already on.
 *
 * Brightness has two modes: uncontrolled (default) writes the device snapshot directly (Home /
 * Advanced); controlled (bind `brightness` + listen to the outputs) lets the parent own it — the
 * Lighting editor uses this so brightness flows through its local config instead of fighting it.
 */
@Component({
  selector: 'pc-bottom-bar',
  standalone: true,
  imports: [IconComponent, SliderComponent, SelectComponent],
  template: `
    <footer class="strip">
      @if (showBrightness()) {
        <pc-icon name="sun" [size]="17" [strokeWidth]="1.8" class="sun"></pc-icon>
        <div class="bright">
          <pc-slider [value]="level()" (valueChange)="onBrightnessInput($event)" (changeEnd)="commitBrightness($event)"></pc-slider>
        </div>
        <span class="bright-val mono">{{ level() }}%</span>
      }

      <span class="spacer"></span>

      @if (showProfile()) {
        <pc-select microLabel="PROFILE" [options]="profileOptions()" [value]="currentProfile()"
                   (valueChange)="switchProfile($any($event))" footerLabel="New profile"
                   (footerAction)="newProfile.emit()"></pc-select>
      }
      @if (active() !== 'lighting' && hasLighting()) {
        <button class="pc-btn ghost" (click)="go('lighting')">
          <pc-icon name="ring" [size]="14" class="amber"></pc-icon> Lighting
        </button>
      }
      @if (active() !== 'advanced') {
        <button class="pc-btn primary" (click)="go('advanced')">Advanced</button>
      }
    </footer>
  `,
  styles: [`
    :host { display: block; flex: none; }
    .strip { height: 74px; display: flex; align-items: center; gap: 20px; padding: 0 24px; border-top: 1px solid var(--line-soft); background: #0D0E12; }
    .sun { color: var(--accent); }
    .bright { width: 200px; }
    .bright-val { font-size: 12.5px; color: var(--accent); min-width: 34px; }
    .spacer { flex: 1; }
    .amber { color: var(--accent); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BottomBarComponent {
  private readonly state = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly capsService = inject(DeviceCapabilitiesService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly serial = input.required<string>();
  /** Show the profile selector (Home only). */
  readonly showProfile = input<boolean>(false);
  /** Current screen, so its own nav button is hidden. */
  readonly active = input<'home' | 'lighting' | 'advanced' | ''>('');
  /** Controlled brightness value. When non-null the parent owns it (emits below, no device write). */
  readonly brightness = input<number | null>(null);
  /** Fired when the user picks "New profile" in the selector footer. */
  readonly newProfile = output<void>();
  /** Controlled-mode brightness: live drag and commit (changeEnd). */
  readonly brightnessChange = output<number>();
  readonly brightnessCommit = output<number>();

  private readonly snap = this.state.snapshotFor(this.serial);
  private readonly caps = this.capsService.forSerial(this.serial);
  private readonly pendingBrightness = signal<number | null>(null);
  private readonly controlled = computed(() => this.brightness() !== null);

  /** Brightness slider shown only when the device has a global brightness control. */
  readonly showBrightness = this.caps.hasGlobalBrightness;
  /** "Lighting" nav shown only when the device supports global lighting. */
  readonly hasLighting = this.caps.hasGlobalLighting;

  readonly level = computed(() => this.controlled()
    ? (this.brightness() ?? 0)
    : (this.pendingBrightness() ?? this.snap()?.lightingConfig?.globalBrightness ?? 0));
  readonly profileOptions = computed<SelectOption[]>(() => (this.snap()?.profiles ?? []).map(p => ({ value: p, label: p })));
  readonly currentProfile = computed(() => this.snap()?.currentProfile ?? '');

  onBrightnessInput(v: number): void {
    if (this.controlled()) { this.brightnessChange.emit(v); return; }
    this.pendingBrightness.set(v);
  }

  commitBrightness(v: number): void {
    if (this.controlled()) { this.brightnessCommit.emit(v); return; }
    const s = this.snap();
    if (!s) return;
    this.deviceService.setLighting(s.serial, { ...s.lightingConfig, globalBrightness: v })
      .subscribe({ error: () => this.toast.show('Could not set brightness', { kind: 'error' }) });
    setTimeout(() => this.pendingBrightness.set(null), 500);
  }

  switchProfile(name: string): void {
    const s = this.snap();
    if (!s || name === s.currentProfile) return;
    this.deviceService.switchProfile(s.serial, name).subscribe({
      next: () => this.toast.show(`Switched to ${name}`, { kind: 'success' }),
      error: () => this.toast.show('Profile switch failed', { kind: 'error' }),
    });
  }

  go(where: 'lighting' | 'advanced'): void {
    this.router.navigate([where === 'lighting' ? '/lighting' : '/device', this.serial()]);
  }
}
