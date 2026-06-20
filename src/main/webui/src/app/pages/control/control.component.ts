import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Location } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { PlatformService } from '../../services/platform.service';
import { Command, Commands, KnobSetting } from '../../models/generated/backend.types';
import {
  AppPickerComponent, IconComponent, StatusDotComponent, ToggleComponent, ToastService,
} from '../../ui';
import { PcKnobComponent } from '../../devices/visual/pc-knob.component';
import { PcFaderComponent } from '../../devices/visual/pc-fader.component';
import { CommandDef, CommandKind, COMMANDS, COMMAND_BY_TYPE, categoryLabel } from '../../features/commands/command-catalog';
import { CommandFieldsComponent } from '../../features/commands/command-fields.component';
import { ControlLightingComponent } from '../../features/lighting/control-lighting.component';
import { analogPct } from '../../devices/visual/device-visual.util';
import { OverlayModule } from '@angular/cdk/overlay';

type Slot = 'rotate' | 'press' | 'dblpress';

interface MenuRow { def: CommandDef; status?: 'ok' | 'idle' | 'connecting'; offline: boolean; }

const EMPTY: Commands = { commands: [], type: 'allAtOnce' };
const EMPTY_KNOB: KnobSetting = { minTrim: 0, maxTrim: 100, logarithmic: false, overlayIcon: '', buttonDebounce: 0 };

@Component({
  selector: 'app-control',
  standalone: true,
  imports: [
    DragDropModule, OverlayModule, RouterLink, IconComponent, ToggleComponent,
    StatusDotComponent, AppPickerComponent, PcKnobComponent, PcFaderComponent, CommandFieldsComponent,
    ControlLightingComponent,
  ],
  templateUrl: './control.component.html',
  styleUrl: './control.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlComponent {
  private readonly state = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly integrations = inject(IntegrationDataService);
  private readonly platform = inject(PlatformService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  readonly serial = input.required<string>();
  readonly index = input.required<string>();           // analog index as string
  readonly idx = computed(() => +this.index());

  readonly snap = this.state.snapshotFor(this.serial);
  readonly processItems = computed(() => this.integrations.processItems());
  readonly isPro = computed(() => this.snap()?.deviceType === 'PCPANEL_PRO');
  readonly knobCount = computed(() => this.isPro() ? 5 : 4);
  readonly isSlider = computed(() => this.isPro() && this.idx() >= 5);
  readonly sliderNum = computed(() => this.idx() - 5);   // S(n) for sliders

  readonly activeSlot = signal<Slot>('rotate');
  readonly expanded = signal<number>(0);
  readonly railCollapsed = signal(false);
  readonly lightingMode = computed(() => this.snap()?.lightingConfig?.lightingMode ?? null);
  readonly isCustomLighting = computed(() => this.lightingMode() === 'CUSTOM');
  readonly query = signal('');
  readonly addOpen = signal(false);
  readonly iconPickerOpen = signal(false);

  // Local editable state, seeded once per (serial,index).
  readonly rotate = signal<Commands>(EMPTY);
  readonly press = signal<Commands>(EMPTY);
  readonly dblpress = signal<Commands>(EMPTY);
  readonly knob = signal<KnobSetting>(EMPTY_KNOB);
  private loadedKey = '';
  private saveTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    effect(() => {
      const s = this.snap();
      const i = this.idx();
      const key = `${this.serial()}:${i}`;
      if (!s || key === this.loadedKey) return;
      this.loadedKey = key;
      const snapProfile = s.currentProfileSnapshot;
      untracked(() => {
        this.rotate.set(clone(snapProfile?.dialData?.[String(i)]) ?? { commands: [], type: 'allAtOnce' });
        this.press.set(clone(snapProfile?.buttonData?.[String(i)]) ?? { commands: [], type: 'allAtOnce' });
        this.dblpress.set(clone(snapProfile?.dblButtonData?.[String(i)]) ?? { commands: [], type: 'allAtOnce' });
        this.knob.set({ ...EMPTY_KNOB, ...(snapProfile?.knobSettings?.[String(i)] ?? {}) });
        this.activeSlot.set('rotate');
        this.expanded.set(0);
      });
    });
  }

  // ── derived ──────────────────────────────────────────────────────────────
  readonly title = computed(() => this.isSlider() ? `S${this.sliderNum() + 1}` : `K${this.idx() + 1}`);
  readonly controlTypeLabel = computed(() => this.isSlider() ? 'Slider' : 'Knob');
  readonly valuePct = computed(() => Math.round(analogPct(this.snap()?.analogValues?.[this.idx()])));
  readonly ledColor = computed(() => {
    const s = this.snap();
    if (this.isSlider()) return s?.sliderColors?.[this.sliderNum()]?.[0] ?? '#FFB020';
    return s?.dialColors?.[this.idx()] ?? '#FFB020';
  });
  readonly actionCount = computed(() => this.rotate().commands.length + this.press().commands.length + this.dblpress().commands.length);

  readonly slotKind = computed<CommandKind>(() => this.activeSlot() === 'rotate' ? 'dial' : 'button');

  readonly activeCommands = computed<Command[]>(() => this.currentSlotSignal()().commands);

  readonly menuGroups = computed(() => {
    const kind = this.slotKind();
    const q = this.query().trim().toLowerCase();
    const defs = COMMANDS.filter(d =>
      d.kinds.includes(kind)
      && (!q || d.label.toLowerCase().includes(q))
      && this.platformAllows(d));
    const cats: ('audio' | 'system' | 'integration')[] = ['audio', 'system', 'integration'];
    return cats.map(cat => ({
      label: categoryLabel(cat),
      rows: defs.filter(d => d.category === cat).map(d => this.toMenuRow(d)),
    })).filter(g => g.rows.length);
  });

  /** Hide platform-specific integration commands on unsupported hosts. */
  private platformAllows(def: CommandDef): boolean {
    if (def.integration === 'voicemeeter') return this.platform.voicemeeterSupported();
    if (def.integration === 'wavelink') return this.platform.waveLinkSupported();
    return true;
  }

  private toMenuRow(def: CommandDef): MenuRow {
    if (!def.integration) return { def, offline: false };
    // Honest status: green only with positive evidence; Voicemeeter has no live signal.
    if (def.integration === 'voicemeeter') return { def, status: 'idle', offline: false };
    const connected = def.integration === 'obs' ? this.integrations.obsConnected() : this.integrations.waveLinkConnected();
    const loading = def.integration === 'obs' ? this.integrations.obsScenes.isLoading() : this.integrations.waveLink.isLoading();
    if (loading) return { def, status: 'connecting', offline: false };
    return { def, status: connected ? 'ok' : 'idle', offline: !connected };
  }

  defFor(cmd: Command): CommandDef | undefined { return COMMAND_BY_TYPE.get(cmd._type); }
  labelFor(cmd: Command): string { return this.defFor(cmd)?.label ?? cmd._type.split('.').pop() ?? '?'; }
  iconFor(cmd: Command) { return this.defFor(cmd)?.icon ?? 'zap'; }

  // ── slot helpers ───────────────────────────────────────────────────────────
  currentSlotSignal() {
    return this.activeSlot() === 'rotate' ? this.rotate : this.activeSlot() === 'press' ? this.press : this.dblpress;
  }

  setSlot(slot: Slot): void { this.activeSlot.set(slot); this.expanded.set(0); }

  // ── mutations ────────────────────────────────────────────────────────────
  addCommand(def: CommandDef): void {
    const sig = this.currentSlotSignal();
    const next = { ...sig(), commands: [...sig().commands, def.buildEmpty() as Command] };
    sig.set(next);
    this.expanded.set(next.commands.length - 1);
    this.addOpen.set(false);
    this.query.set('');
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
    this.deviceService.setControlAssignments(s.serial, s.currentProfile, this.idx(), {
      analog: this.rotate(),
      button: this.press(),
      dblButton: this.dblpress(),
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
