import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OverlayModule } from '@angular/cdk/overlay';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { AppPickerComponent, BottomBarComponent, IconComponent, MenuComponent, MenuItem, ModalComponent, ToastService, ToggleComponent } from '../../ui';
import { shortLabel } from '../../devices/visual/device-visual.util';
import { DeviceCapabilitiesService } from '../../services/device-capabilities.service';
import { Commands, ProfileSettingsDto } from '../../models/generated/backend.types';

const EMPTY: Commands = { commands: [], type: 'allAtOnce' };

interface SlotLine { label: string; text: string; cls: string; }

@Component({
  selector: 'app-device',
  standalone: true,
  imports: [OverlayModule, DragDropModule, IconComponent, MenuComponent, ModalComponent, ToggleComponent, AppPickerComponent, BottomBarComponent],
  templateUrl: './device.component.html',
  styleUrl: './device.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceComponent {
  private readonly state = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly integrations = inject(IntegrationDataService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly capsService = inject(DeviceCapabilitiesService);

  readonly serial = input.required<string>();

  readonly snap = this.state.snapshotFor(this.serial);
  private readonly caps = this.capsService.forSerial(this.serial);

  constructor() {
    // Load the current profile's activation settings whenever the active profile changes.
    effect(() => {
      const serial = this.serial();
      const profile = this.currentProfile();
      if (!serial || !profile) return;
      const key = `${serial}:${profile}`;
      if (key === this.settingsKey) return;
      this.settingsKey = key;
      this.deviceService.getProfileSettings(serial, profile).subscribe({
        next: s => this.profileSettings.set(s),
        error: () => this.profileSettings.set(null),
      });
    });
  }

  readonly isPro = this.caps.isProLayout;
  readonly knobCount = this.caps.knobCount;
  readonly sliderCount = this.caps.sliderCount;
  readonly knobIdx = computed(() => Array.from({ length: this.knobCount() }, (_, i) => i));
  /** Slider positions (0-based); the analog index is sliderAnalogIdx(j). */
  readonly sliderIdx = computed(() => Array.from({ length: this.sliderCount() }, (_, j) => j));
  /** Analog index of the j-th slider (Pro slider 0 → 5). */
  sliderAnalogIdx(j: number): number { return this.caps.sliderIndexAt(j); }

  readonly currentProfile = computed(() => this.snap()?.currentProfile ?? '');

  // which ⋮ menu is open, keyed by 'k0' / 's0' …; null = none.
  readonly openMenu = signal<string | null>(null);

  readonly newProfileOpen = signal(false);
  readonly newProfileName = signal('');
  readonly pendingDelete = signal<string | null>(null);
  readonly renameOpen = signal(false);
  readonly renameValue = signal('');

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
    return this.slotText(p?.dialData?.[String(this.sliderAnalogIdx(j))]);
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
    const idx = this.sliderAnalogIdx(j);
    if (item.value === 'configure') { this.configure(idx); return; }
    if (item.value === 'clear') { this.clearControl(idx); }
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

  /** Drag-reorder the profile chips; persist the new order (no WS echo for this). */
  dropProfile(ev: CdkDragDrop<string[]>): void {
    if (ev.previousIndex === ev.currentIndex) return;
    const serial = this.serial();
    const order = [...this.profiles()];
    moveItemInArray(order, ev.previousIndex, ev.currentIndex);
    this.state.patchProfiles(serial, () => order);          // optimistic
    this.deviceService.reorderProfiles(serial, order)
      .subscribe({ error: () => this.toast.show('Could not reorder profiles', { kind: 'error' }) });
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

  // ── rename ────────────────────────────────────────────────────────────────
  openRename(): void {
    const cur = this.currentProfile();
    if (!cur) return;
    this.renameValue.set(cur);
    this.renameOpen.set(true);
  }

  commitRename(): void {
    const serial = this.serial();
    const oldName = this.currentProfile();
    const newName = this.renameValue().trim();
    this.renameOpen.set(false);
    if (!serial || !oldName || !newName || newName === oldName) return;
    if (this.profiles().includes(newName)) {
      this.toast.show('A profile with that name already exists', { kind: 'error' });
      return;
    }
    this.deviceService.renameProfile(serial, oldName, newName).subscribe({
      next: () => {
        this.state.patchProfileRename(serial, oldName, newName);
        this.toast.show(`Renamed to ${newName}`, { kind: 'success' });
      },
      error: () => this.toast.show('Could not rename profile', { kind: 'error' }),
    });
  }

  // ── profile management ──────────────────────────────────────────────────────
  /** Any profile can be deleted (incl. the active one) except the last remaining one. */
  readonly canDelete = computed(() => this.profiles().length > 1);

  requestDelete(name: string): void {
    if (this.canDelete()) this.pendingDelete.set(name);
  }

  deleteCurrent(): void { this.requestDelete(this.currentProfile()); }

  confirmDelete(): void {
    const serial = this.serial();
    const name = this.pendingDelete();
    this.pendingDelete.set(null);
    if (!name) return;
    const profiles = this.profiles();
    if (profiles.length <= 1) return;                 // never delete the last profile

    const remove = () => {
      this.deviceService.deleteProfile(serial, name).subscribe({
        next: () => {
          this.state.patchProfiles(serial, ps => ps.filter(p => p !== name));
          this.toast.show(`Deleted profile "${name}"`, { kind: 'success' });
        },
        error: () => this.toast.show('Could not delete profile', { kind: 'error' }),
      });
    };

    // Deleting the active profile: switch to the next one first, then remove it.
    if (name === this.currentProfile()) {
      const i = profiles.indexOf(name);
      const next = profiles[i + 1] ?? profiles[i - 1];
      this.deviceService.switchProfile(serial, next).subscribe({
        next: remove,
        error: () => this.toast.show('Could not switch profile', { kind: 'error' }),
      });
      return;
    }
    remove();
  }

  // ── per-profile activation settings ──────────────────────────────────────────
  readonly profileSettings = signal<ProfileSettingsDto | null>(null);
  private settingsKey = '';
  readonly appPickerOpen = signal(false);
  readonly newApp = signal('');
  readonly processItems = computed(() => this.integrations.processItems());

  private saveSettings(next: ProfileSettingsDto): void {
    this.profileSettings.set(next);
    this.deviceService.setProfileSettings(this.serial(), next.name, next)
      .subscribe({ error: () => this.toast.show('Could not save profile settings', { kind: 'error' }) });
  }

  setMain(on: boolean): void { const s = this.profileSettings(); if (s) this.saveSettings({ ...s, isMainProfile: on }); }
  setBaseLayer(on: boolean): void { const s = this.profileSettings(); if (s) this.saveSettings({ ...s, isBaseLayer: on }); }
  setFocusBack(on: boolean): void { const s = this.profileSettings(); if (s) this.saveSettings({ ...s, focusBackOnLost: on }); }

  addApp(name: string): void {
    const s = this.profileSettings();
    const n = name.trim();
    if (!s || !n || s.activateApplications.some(a => a.toLowerCase() === n.toLowerCase())) return;
    this.saveSettings({ ...s, activateApplications: [...s.activateApplications, n] });
  }

  removeApp(name: string): void {
    const s = this.profileSettings();
    if (!s) return;
    this.saveSettings({ ...s, activateApplications: s.activateApplications.filter(a => a !== name) });
  }

  addManual(): void { this.addApp(this.newApp()); this.newApp.set(''); }
  onAppPicked(name: string): void { this.appPickerOpen.set(false); this.addApp(name); }
}
