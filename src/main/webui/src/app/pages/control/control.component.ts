import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Location } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { DeviceCapabilitiesService } from '../../services/device-capabilities.service';
import { Command, Commands, KnobSetting } from '../../models/generated/backend.types';
import {
  AppPickerComponent, IconComponent, ToggleComponent, ToastService,
} from '../../ui';
import { PcKnobComponent } from '../../devices/visual/pc-knob.component';
import { PcFaderComponent } from '../../devices/visual/pc-fader.component';
import { CommandDef, CommandKind, COMMAND_BY_TYPE } from '../../features/commands/command-catalog';
import { CommandFieldsComponent } from '../../features/commands/command-fields.component';
import { CommandPickerComponent } from '../../features/commands/command-picker.component';
import { MappingPreviewComponent } from '../../features/commands/mapping-preview.component';
import { DialParams } from '../../features/commands/mapping-curve.util';
import { ControlLightingComponent } from '../../features/lighting/control-lighting.component';
import { analogPct, describeCommand } from '../../devices/visual/device-visual.util';
import { OverlayModule } from '@angular/cdk/overlay';

type Slot = 'rotate' | 'press' | 'dblpress' | 'release';

const EMPTY: Commands = { commands: [], type: 'allAtOnce' };
const EMPTY_KNOB: KnobSetting = { minTrim: 0, maxTrim: 100, logarithmic: false, overlayIcon: '', buttonDebounce: 0 };

@Component({
  selector: 'app-control',
  standalone: true,
  imports: [
    DragDropModule, OverlayModule, RouterLink, IconComponent, ToggleComponent,
    AppPickerComponent, PcKnobComponent, PcFaderComponent, CommandFieldsComponent,
    ControlLightingComponent, MappingPreviewComponent, CommandPickerComponent,
  ],
  templateUrl: './control.component.html',
  styleUrl: './control.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlComponent {
  private readonly state = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly integrations = inject(IntegrationDataService);
  private readonly capsService = inject(DeviceCapabilitiesService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly serial = input.required<string>();
  readonly index = input.required<string>();           // analog index as string
  readonly idx = computed(() => +this.index());
  readonly slot = input<string>('');                   // optional ?slot= query param: open that tab
  readonly editProfile = input<string>('', { alias: 'profile' }); // optional ?profile=: edit this profile (e.g. the base layer) instead of the active one

  /** The profile actually being edited: the ?profile= override, else the device's active profile. */
  readonly targetProfile = computed(() => this.editProfile() || this.snap()?.currentProfile || '');
  /** True when editing a profile other than the active one (so the UI can flag it). */
  readonly editingOtherProfile = computed(() => !!this.editProfile() && this.editProfile() !== (this.snap()?.currentProfile ?? ''));

  readonly snap = this.state.snapshotFor(this.serial);
  private readonly caps = this.capsService.forSerial(this.serial);
  readonly processItems = computed(() => this.integrations.processItems());
  readonly isPro = this.caps.isProLayout;
  readonly knobCount = this.caps.knobCount;
  readonly isSlider = computed(() => this.caps.isSlider(this.idx()));
  /** Position of this control among the device's sliders (S(n)), or -1 if it isn't one. */
  readonly sliderNum = computed(() => this.caps.sliderNumber(this.idx()));

  readonly activeSlot = signal<Slot>('rotate');
  readonly expanded = signal<number>(0);
  readonly lightingMode = computed(() => this.snap()?.lightingConfig?.lightingMode ?? null);
  readonly isCustomLighting = computed(() => this.lightingMode() === 'CUSTOM');
  readonly lightingModeLabel = computed(() => ({
    ALL_COLOR: 'Solid color', ALL_RAINBOW: 'Rainbow', ALL_WAVE: 'Wave',
    ALL_BREATH: 'Breath', SINGLE_COLOR: 'Per-LED color', CUSTOM: 'Per-control',
  }[this.lightingMode() ?? ''] ?? 'a global'));
  readonly iconPickerOpen = signal(false);

  // Local editable state, seeded once per (serial,index).
  readonly rotate = signal<Commands>(EMPTY);
  readonly press = signal<Commands>(EMPTY);
  readonly dblpress = signal<Commands>(EMPTY);
  readonly release = signal<Commands>(EMPTY);
  readonly knob = signal<KnobSetting>(EMPTY_KNOB);
  private loadedKey = '';
  private saveTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      const s = this.snap();
      const i = this.idx();
      const editing = this.editProfile();
      const key = `${this.serial()}:${i}:${editing}`;
      if (!s || key === this.loadedKey) return;
      this.loadedKey = key;
      // Seed from the profile being edited: the base-layer snapshot when ?profile= targets it, else the active one.
      const snapProfile = editing && s.baseLayerSnapshot?.name === editing ? s.baseLayerSnapshot : s.currentProfileSnapshot;
      untracked(() => {
        this.rotate.set(clone(snapProfile?.dialData?.[String(i)]) ?? { commands: [], type: 'allAtOnce' });
        this.press.set(clone(snapProfile?.buttonData?.[String(i)]) ?? { commands: [], type: 'allAtOnce' });
        this.dblpress.set(clone(snapProfile?.dblButtonData?.[String(i)]) ?? { commands: [], type: 'allAtOnce' });
        this.release.set(clone(snapProfile?.releaseButtonData?.[String(i)]) ?? { commands: [], type: 'allAtOnce' });
        this.knob.set({ ...EMPTY_KNOB, ...(snapProfile?.knobSettings?.[String(i)] ?? {}) });
        const want = this.slot();
        this.activeSlot.set(want === 'press' || want === 'dblpress' || want === 'release' ? want : 'rotate');
        this.expanded.set(0);
      });
    });
  }

  // ── derived ──────────────────────────────────────────────────────────────
  readonly title = computed(() => this.isSlider() ? `S${this.sliderNum() + 1}` : `K${this.idx() + 1}`);
  readonly controlTypeLabel = computed(() => this.isSlider() ? 'Slider' : 'Knob');
  readonly valuePct = computed(() =>
    // The WS snapshot normalizes every provider's analog value to the canonical
    // 0–255 domain at the backend edge, so display scales against 0–255 — NOT the
    // descriptor's raw sourceMin/sourceMax (which is the pre-normalization range).
    Math.round(analogPct(this.snap()?.analogValues?.[this.idx()])));
  /** Output after logarithmic scaling (mirrors backend DialValueCalculator), as a %.
   *  This is PCPanel firmware math in the canonical 0–255 domain. */
  readonly actualPct = computed(() => {
    const raw = this.snap()?.analogValues?.[this.idx()] ?? 0;        // 0–255
    const logged = (Math.round(Math.pow(1.04723275, raw / 2.55)) - 1) * 2.55;
    return Math.round(Math.max(0, Math.min(255, logged)) / 255 * 100);
  });
  readonly ledColor = computed(() => {
    const s = this.snap();
    if (this.isSlider()) return s?.sliderColors?.[this.sliderNum()]?.[0] ?? '#FFB020';
    return s?.dialColors?.[this.idx()] ?? '#FFB020';
  });
  readonly actionCount = computed(() => this.rotate().commands.length + this.press().commands.length + this.dblpress().commands.length + this.release().commands.length);

  readonly slotKind = computed<CommandKind>(() => this.activeSlot() === 'rotate' ? 'dial' : 'button');

  readonly activeCommands = computed<Command[]>(() => this.currentSlotSignal()().commands);

  /** The all-at-once / in-sequence mode only matters for a press slot holding more than one
   *  action — a dial runs its mapped value, and a single action is identical either way. */
  readonly showSeqToggle = computed(() => this.activeSlot() !== 'rotate' && this.activeCommands().length > 1);

  defFor(cmd: Command): CommandDef | undefined { return COMMAND_BY_TYPE.get(cmd._type); }
  labelFor(cmd: Command): string {
    // Prefer a label that names the actual target ("Music — Wave Link"); fall back to the generic
    // catalog label for commands with nothing meaningful to name yet.
    return describeCommand(cmd, this.integrations) || this.defFor(cmd)?.label || cmd._type.split('.').pop() || '?';
  }
  iconFor(cmd: Command) { return this.defFor(cmd)?.icon ?? 'zap'; }

  // ── slot helpers ───────────────────────────────────────────────────────────
  currentSlotSignal() {
    switch (this.activeSlot()) {
      case 'rotate': return this.rotate;
      case 'press': return this.press;
      case 'dblpress': return this.dblpress;
      case 'release': return this.release;
    }
  }

  setSlot(slot: Slot): void { this.activeSlot.set(slot); this.expanded.set(0); }

  /** Flip the active slot between firing every action at once and firing one per press (rotating). */
  toggleSlotMode(): void {
    const sig = this.currentSlotSignal();
    sig.set({ ...sig(), type: sig().type === 'sequential' ? 'allAtOnce' : 'sequential' });
    this.save();
  }

  // ── mutations ────────────────────────────────────────────────────────────
  addCommand(def: CommandDef): void {
    const sig = this.currentSlotSignal();
    const next = { ...sig(), commands: [...sig().commands, def.buildEmpty() as Command] };
    sig.set(next);
    this.expanded.set(next.commands.length - 1);
    this.save();
  }

  removeCommand(i: number): void {
    const sig = this.currentSlotSignal();
    sig.set({ ...sig(), commands: sig().commands.filter((_, k) => k !== i) });
    this.save();
  }

  updateCommand(i: number, cmd: Record<string, any>): void {
    const sig = this.currentSlotSignal();
    const cmds = [...sig().commands];
    cmds[i] = cmd as unknown as Command;
    sig.set({ ...sig(), commands: cmds });
    this.save();
  }

  toggleExpand(i: number): void { this.expanded.set(this.expanded() === i ? -1 : i); }

  // The whole header row is both a drag handle and a click target; the browser fires a
  // click after a real drag, so swallow that one so reordering doesn't also toggle expand.
  private dragging = false;
  onDragStarted(): void { this.dragging = true; }
  onDragEnded(): void { setTimeout(() => (this.dragging = false)); }
  onHeadClick(i: number): void { if (this.dragging) return; this.toggleExpand(i); }

  /** dialParams to preview in the action header — rotate slot only, and only for
   *  commands that actually map a range (those carry a dialParams object). */
  mapParams(cmd: Command): DialParams | null {
    return this.activeSlot() === 'rotate' ? ((cmd as Record<string, any>)['dialParams'] ?? null) : null;
  }

  drop(ev: CdkDragDrop<Command[]>): void {
    const sig = this.currentSlotSignal();
    const cmds = [...sig().commands];
    moveItemInArray(cmds, ev.previousIndex, ev.currentIndex);
    sig.set({ ...sig(), commands: cmds });
    this.save();
  }

  // ── knob settings ──────────────────────────────────────────────────────────
  setKnob<K extends keyof KnobSetting>(key: K, value: KnobSetting[K]): void {
    this.knob.update(k => ({ ...k, [key]: value }));
    this.save();
  }

  setOverlayIcon(name: string): void {
    this.setKnob('overlayIcon', name);
    this.iconPickerOpen.set(false);
  }

  // ── persistence ──────────────────────────────────────────────────────────
  private save(): void {
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.flush(), 350);
  }

  private flush(): void {
    const s = this.snap();
    if (!s) return;
    // Save to the profile being edited (the active one, or the base layer when reached via its chip).
    this.deviceService.setControlAssignments(s.serial, this.targetProfile(), this.idx(), {
      analog: this.rotate(),
      button: this.press(),
      dblButton: this.dblpress(),
      releaseButton: this.release(),
      knobSetting: this.knob(),
    }).subscribe({ error: () => this.toast.show('Could not save assignment', { kind: 'error' }) });
  }

  /** Return to wherever we came from (Home or Advanced), not always Advanced. */
  back(): void {
    if (history.length > 1) this.location.back();
    else this.router.navigate(['/']);
  }
}

function clone<T>(v: T | undefined): T | undefined {
  return v === undefined ? undefined : JSON.parse(JSON.stringify(v));
}
