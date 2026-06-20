import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OverlayModule } from '@angular/cdk/overlay';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import { IconComponent, MenuComponent, MenuItem, ModalComponent, ToastService } from '../../ui';
import { shortLabel } from '../../devices/visual/device-visual.util';
import { DebugService } from '../../services/debug.service';
import { Commands } from '../../models/generated/backend.types';

const EMPTY: Commands = { commands: [], type: 'allAtOnce' };

interface SlotLine { label: string; text: string; cls: string; }

@Component({
  selector: 'app-device',
  standalone: true,
  imports: [OverlayModule, IconComponent, MenuComponent, ModalComponent],
  templateUrl: './device.component.html',
  styleUrl: './device.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceComponent {
  private readonly state = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly debug = inject(DebugService);

  readonly serial = input.required<string>();

  readonly snap = this.state.snapshotFor(this.serial);

  readonly isPro = computed(() => (this.debug.deviceTypeOverride() || this.snap()?.deviceType) === 'PCPANEL_PRO');
  readonly knobCount = computed(() => this.isPro() ? 5 : 4);
  readonly knobIdx = computed(() => Array.from({ length: this.knobCount() }, (_, i) => i));
  readonly sliderIdx = computed(() => this.isPro() ? [0, 1, 2, 3] : []);

  readonly currentProfile = computed(() => this.snap()?.currentProfile ?? '');

  // which ⋮ menu is open, keyed by 'k0' / 's0' …; null = none.
  readonly openMenu = signal<string | null>(null);

  readonly newProfileOpen = signal(false);
  readonly newProfileName = signal('');
  readonly manageOpen = signal(false);
  readonly pendingDelete = signal<string | null>(null);

  readonly profiles = computed(() => this.snap()?.profiles ?? []);

  // ── per-control assignment text ──────────────────────────────────────────
  private slotText(cmds: Commands | null | undefined): string {
    const l = shortLabel(cmds);
    return l || '—';
  }

  knobLines(i: number): SlotLine[] {
    const p = this.snap()?.currentProfileSnapshot;
    const key = String(i);
    return [
      { label: 'ROTATE', text: this.slotText(p?.dialData?.[key]), cls: 'rotate' },
      { label: 'PRESS', text: this.slotText(p?.buttonData?.[key]), cls: 'press' },
      { label: '2× PRESS', text: this.slotText(p?.dblButtonData?.[key]), cls: 'dbl' },
    ];
  }

  knobColor(i: number): string {
    return this.snap()?.dialColors?.[i] ?? '#FFB020';
  }

  sliderColor(j: number): string {
    return this.snap()?.sliderColors?.[j]?.[0] ?? '#FFB020';
  }

  sliderRotateText(j: number): string {
    const p = this.snap()?.currentProfileSnapshot;
    return this.slotText(p?.dialData?.[String(j + 5)]);
  }

  // ── menus ────────────────────────────────────────────────────────────────
  readonly knobMenuItems: MenuItem[] = [
    { label: 'Configure', icon: 'sliders', value: 'configure' },
    { label: 'Clear control', icon: 'eraser', value: 'clear', danger: true, separatorBefore: true },
  ];

  readonly sliderMenuItems: MenuItem[] = [
    { label: 'Configure', icon: 'sliders', value: 'configure' },
    { label: 'Clear control', icon: 'eraser', value: 'clear', danger: true, separatorBefore: true },
  ];

  toggleMenu(key: string): void {
    this.openMenu.set(this.openMenu() === key ? null : key);
  }

  /** Reflect the menu's own open/close (e.g. backdrop click) back into our state. */
  menuOpenChange(key: string, open: boolean): void {
    if (open) this.openMenu.set(key);
    else if (this.openMenu() === key) this.openMenu.set(null);
  }

  onKnobMenu(i: number, item: MenuItem): void {
    if (item.value === 'configure') { this.configure(i); return; }
    if (item.value === 'clear') { this.clearControl(i); }
  }

  onSliderMenu(j: number, item: MenuItem): void {
    if (item.value === 'configure') { this.configure(j + 5); return; }
    if (item.value === 'clear') { this.clearControl(j + 5); }
  }

  // ── navigation ─────────────────────────────────────────────────────────────
  back(): void { this.router.navigate(['/']); }

  configure(analogIndex: number): void {
    this.router.navigate(['/control', this.serial(), analogIndex]);
  }

  // ── mutations ────────────────────────────────────────────────────────────
  clearControl(i: number): void {
    const s = this.snap();
    if (!s) return;
    this.deviceService.setControlAssignments(s.serial, s.currentProfile, i, {
      analog: { ...EMPTY },
      button: { ...EMPTY },
      dblButton: { ...EMPTY },
    }).subscribe({ error: () => this.toast.show('Could not clear control', { kind: 'error' }) });
  }

  switchProfile(name: string): void {
    const s = this.snap();
    if (!s || name === s.currentProfile) return;
    this.deviceService.switchProfile(s.serial, name).subscribe({
      next: () => this.toast.show(`Switched to ${name}`, { kind: 'success' }),
      error: () => this.toast.show('Profile switch failed', { kind: 'error' }),
    });
  }

  createProfile(): void {
    const serial = this.serial();
    const name = this.newProfileName().trim();
    this.newProfileOpen.set(false);
    this.newProfileName.set('');
    if (!name) return;
    this.deviceService.createProfile(serial, name).subscribe({
      next: () => {
        this.state.patchProfiles(serial, ps => ps.includes(name) ? ps : [...ps, name]);
        this.deviceService.switchProfile(serial, name).subscribe({
          error: () => this.toast.show('Profile switch failed', { kind: 'error' }),
        });
      },
      error: () => this.toast.show('Could not create profile', { kind: 'error' }),
    });
  }

  // ── profile management ──────────────────────────────────────────────────────
  /** Active profile and last-remaining profile can't be deleted. */
  canDelete(name: string): boolean {
    return name !== this.currentProfile() && this.profiles().length > 1;
  }

  requestDelete(name: string): void {
    if (this.canDelete(name)) this.pendingDelete.set(name);
  }

  confirmDelete(): void {
    const serial = this.serial();
    const name = this.pendingDelete();
    this.pendingDelete.set(null);
    if (!name) return;
    this.deviceService.deleteProfile(serial, name).subscribe({
      next: () => {
        this.state.patchProfiles(serial, ps => ps.filter(p => p !== name));
        this.toast.show(`Deleted profile "${name}"`, { kind: 'success' });
      },
      error: () => this.toast.show('Could not delete profile', { kind: 'error' }),
    });
  }
}
