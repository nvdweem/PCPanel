import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of, switchMap, timer } from 'rxjs';
import { ColorPickerComponent, IconComponent, IconName, SegmentedComponent, SegmentOption, SelectComponent, SelectOption } from '../../ui';
import { IntegrationDataService } from '../commands/integration-data.service';
import { VisualizerConfig, VisualizerSource, VisualizerStyle as Style, VisualizerWhen as When } from '../../models/generated/backend.types';

interface VisualizerStatus {
  available: boolean;
  reason?: string | null;
  defaultOutput?: string | null;
  defaultInput?: string | null;
  /** The source it is capturing right now. */
  listening?: VisualizerSource | null;
}
interface Preset { name: string; style: Style; low?: string; high?: string; }
interface LightChip { key: string; label: string; }

const DEFAULT_LOW = '#2040FF';
const DEFAULT_HIGH = '#FF2D95';
/** How often the card asks which source it hears. */
const STATUS_POLL_MS = 2000;

/** A source as one string, for the add menu and for comparing. */
export function sourceKey(s: VisualizerSource): string {
  switch (s.kind) {
    case 'OUTPUT': return `out:${s.device ?? ''}`;
    case 'INPUT': return `in:${s.device ?? ''}`;
    case 'APP': return `app:${s.app ?? ''}`;
    default: return 'any';
  }
}

/** The source a {@link sourceKey} names. */
export function sourceOf(key: string): VisualizerSource {
  const value = key.slice(key.indexOf(':') + 1);
  if (key.startsWith('out:')) return value ? { kind: 'OUTPUT', device: value } : { kind: 'OUTPUT' };
  if (key.startsWith('in:')) return value ? { kind: 'INPUT', device: value } : { kind: 'INPUT' };
  if (key.startsWith('app:')) return { kind: 'APP', app: value };
  return { kind: 'ANY_APP' };
}

/** The visualizer's presets: they set the style and colours, nothing else. */
export const VISUALIZER_PRESETS: Preset[] = [
  { name: 'Party', style: 'RAINBOW' },
  { name: 'Neon Club', style: 'TWO_COLORS', low: '#6A00FF', high: '#00FFF0' },
  { name: 'Bonfire', style: 'TWO_COLORS', low: '#FF1A00', high: '#FFD000' },
  { name: 'Synthwave', style: 'TWO_COLORS', low: '#FF00C8', high: '#FFB000' },
  { name: 'Toxic', style: 'TWO_COLORS', low: '#00FF40', high: '#EAFF00' },
  { name: 'Heartbeat', style: 'PULSE', high: '#FF1A3C' },
  { name: 'Deep Sea', style: 'PULSE', high: '#1A8CFF' },
  { name: 'Strobe', style: 'PULSE', high: '#FFFFFF' },
];

/**
 * The "Music visualizer" block of the Lighting page: when it shows, what it listens to, on which lights, and how it looks.
 * Edits the profile's {@link VisualizerConfig}; the page saves it with the rest of the lighting.
 */
@Component({
  selector: 'pc-visualizer-card',
  standalone: true,
  imports: [ColorPickerComponent, IconComponent, SegmentedComponent, SelectComponent],
  template: `
    <div class="block">
      <span class="micro-label">MUSIC VISUALIZER</span>
      @if (status(); as st) {
        @if (!st.available) {
          <span class="hint warn">{{ st.reason || 'The music visualizer is not available here.' }}</span>
        }
      }
      <pc-segmented [options]="whenOptions" [value]="cfg().when" (valueChange)="setWhen($any($event))"
                    [disabled]="status()?.available === false"></pc-segmented>

      @if (cfg().when !== 'OFF') {
        <span class="lbl">Listens to</span>
        <div class="sources">
          @for (src of cfg().sources; track sourceKey(src); let i = $index, first = $first, last = $last) {
            <div class="src" [class.on]="isListening(src)">
              @if (appIcon(src); as icon) {
                <img class="ico" [src]="icon" alt="">
              } @else {
                <pc-icon class="kind" [name]="kindIcon(src)" [size]="15"></pc-icon>
              }
              <span class="name">{{ sourceLabel(src) }}</span>
              @if (isListening(src)) { <span class="listening">listening</span> }
              <button class="pc-icon-btn sm" title="Move up" [disabled]="first" (click)="move(i, -1)"><pc-icon name="chevron-up" [size]="13"></pc-icon></button>
              <button class="pc-icon-btn sm" title="Move down" [disabled]="last" (click)="move(i, 1)"><pc-icon name="chevron-down" [size]="13"></pc-icon></button>
              <button class="pc-icon-btn sm" title="Remove" (click)="remove(i)"><pc-icon name="x" [size]="13"></pc-icon></button>
            </div>
          }
          @for (k of [addRound()]; track k) {
            <pc-select [options]="addOptions()" placeholder="Add a source" [block]="true" [panelWidth]="320"
                       (openChange)="$event && refreshLists()" (valueChange)="add($any($event))"></pc-select>
          }
        </div>
        <span class="hint">It listens to the first one that has sound.</span>

        <span class="lbl">Presets</span>
        <div class="presets">
          @for (p of presets; track p.name) {
            <button class="preset" [class.active]="isPreset(p)" (click)="applyPreset(p)" [title]="p.name">
              <span class="swatch" [style.background]="presetSwatch(p)"></span>
              <span class="name">{{ p.name }}</span>
            </button>
          }
        </div>

        <span class="lbl">Style</span>
        <pc-segmented [options]="styleOptions" [value]="cfg().style" (valueChange)="patch({ style: $any($event) })"></pc-segmented>
        @if (cfg().style === 'TWO_COLORS') {
          <pc-color-picker label="Quiet" [value]="cfg().lowColor || defaultLow" (valueChange)="patch({ lowColor: $event })"></pc-color-picker>
        }
        @if (cfg().style !== 'RAINBOW') {
          <pc-color-picker [label]="cfg().style === 'PULSE' ? 'Color' : 'Loud'" [value]="cfg().highColor || defaultHigh" (valueChange)="patch({ highColor: $event })"></pc-color-picker>
        }

        <span class="lbl">Lights</span>
        @if (perControl()) {
          <div class="chips">
            <button class="pc-chip toggle" [class.on]="!cfg().lights.length" (click)="patch({ lights: [] })">All</button>
            @for (chip of lightChips(); track chip.key) {
              <button class="pc-chip toggle" [class.on]="cfg().lights.includes(chip.key)" (click)="toggleLight(chip.key)">{{ chip.label }}</button>
            }
          </div>
          <span class="hint">Lights you leave out keep their own look.</span>
        } @else {
          <span class="hint">All of them while it shows. To keep some lights as they are, switch the mode to Per-control.</span>
        }
        <span class="hint">Nothing is recorded or saved.</span>
      }
    </div>
  `,
  styles: [`
    .block { display: flex; flex-direction: column; gap: 10px; }
    .micro-label { color: var(--text-3); }
    .lbl { font-size: 13px; color: var(--text-2); margin-top: 4px; }
    .hint { font-size: 11.5px; color: var(--text-2); line-height: 1.45; }
    .hint.warn { color: var(--warn, #FFB020); }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .pc-chip.toggle { cursor: pointer; border: 1px solid var(--line); background: var(--input); color: var(--text-2); font: inherit; font-size: 12px; }
    .pc-chip.toggle.on { background: var(--accent-tint); border-color: var(--accent-border-2); color: var(--accent-text); }
    .sources { display: flex; flex-direction: column; gap: 6px; }
    .src {
      display: flex; align-items: center; gap: 8px; padding: 4px 4px 4px 10px; border-radius: 8px;
      background: var(--panel); border: 1px solid var(--line); font-size: 12.5px; color: var(--text-soft);
    }
    .src.on { border-color: var(--accent-border-2); }
    .src .ico { width: 16px; height: 16px; object-fit: contain; flex: none; }
    .src .kind { color: var(--text-3); flex: none; }
    .src .name { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .src .listening { font-family: var(--font-mono); font-size: 9.5px; color: var(--accent-text); letter-spacing: .04em; }
    .presets { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
    .preset {
      display: flex; align-items: center; gap: 8px; padding: 7px 9px; border-radius: 8px; cursor: pointer; text-align: left;
      background: var(--panel); border: 1px solid var(--line); color: var(--text-soft); font-size: 12px;
    }
    .preset:hover { border-color: var(--line-2); }
    .preset.active { background: var(--accent-tint); border-color: var(--accent-border-2); color: var(--accent-text); }
    .preset .swatch { width: 16px; height: 16px; border-radius: 4px; flex: none; }
    .preset .name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VisualizerCardComponent {
  readonly integrations = inject(IntegrationDataService);
  private readonly http = inject(HttpClient);

  readonly config = input<VisualizerConfig | undefined>(undefined);
  readonly knobCount = input<number>(0);
  readonly sliderCount = input<number>(0);
  readonly hasLabels = input<boolean>(false);
  readonly hasLogo = input<boolean>(false);
  /** Whether the profile's lighting is per control, so single lights can be left out. */
  readonly perControl = input<boolean>(false);
  readonly configChange = output<VisualizerConfig>();

  readonly presets = VISUALIZER_PRESETS;
  readonly defaultLow = DEFAULT_LOW;
  readonly defaultHigh = DEFAULT_HIGH;
  /** Bumped after each pick, so the add menu starts empty again. */
  readonly addRound = signal(0);

  readonly status = toSignal(
    timer(0, STATUS_POLL_MS).pipe(switchMap(() => this.http.get<VisualizerStatus>('/api/visualizer/status').pipe(catchError(() => of(null))))),
    { initialValue: null });

  readonly whenOptions: SegmentOption<When>[] = [
    { value: 'OFF', label: 'Off' },
    { value: 'PLAYING', label: 'While playing' },
    { value: 'ALWAYS', label: 'Always' },
  ];
  readonly styleOptions: SegmentOption<Style>[] = [
    { value: 'RAINBOW', label: 'Rainbow' },
    { value: 'TWO_COLORS', label: 'Two colors' },
    { value: 'PULSE', label: 'Pulse' },
  ];

  readonly cfg = computed<VisualizerConfig>(() => {
    const c = this.config();
    return {
      when: c?.when ?? 'OFF',
      sources: c?.sources ?? [{ kind: 'OUTPUT' }],
      lights: c?.lights ?? [],
      style: c?.style ?? 'RAINBOW',
      lowColor: c?.lowColor ?? DEFAULT_LOW,
      highColor: c?.highColor ?? DEFAULT_HIGH,
    };
  });

  readonly lightChips = computed<LightChip[]>(() => {
    const chips: LightChip[] = [];
    for (let i = 0; i < this.knobCount(); i++) chips.push({ key: `knob:${i}`, label: `K${i + 1}` });
    for (let i = 0; i < this.sliderCount(); i++) chips.push({ key: `slider:${i}`, label: `S${i + 1}` });
    if (this.hasLabels()) for (let i = 0; i < this.sliderCount(); i++) chips.push({ key: `label:${i}`, label: `L${i + 1}` });
    if (this.hasLogo()) chips.push({ key: 'logo', label: 'Logo' });
    return chips;
  });

  patch(part: Partial<VisualizerConfig>): void {
    this.configChange.emit({ ...this.cfg(), ...part });
  }

  setWhen(when: When): void { this.patch({ when }); }

  readonly sourceKey = sourceKey;

  /** The add menu: outputs, inputs, running apps, and whatever plays loudest; what is already listed is greyed. */
  readonly addOptions = computed<SelectOption[]>(() => {
    const taken = new Set(this.cfg().sources.map(sourceKey));
    const st = this.status();
    const opt = (value: string, label: string, group?: string, icon?: string | null): SelectOption =>
      ({ value, label, group, icon, disabled: taken.has(value) });
    return [
      opt('out:', withName('Default output', st?.defaultOutput), 'Outputs'),
      ...(this.integrations.outputDevices.value() ?? []).map(d => opt(`out:${d.id}`, d.name, 'Outputs')),
      opt('in:', withName('Default input', st?.defaultInput), 'Inputs'),
      ...(this.integrations.inputDevices.value() ?? []).map(d => opt(`in:${d.id}`, d.name, 'Inputs')),
      ...this.integrations.processItems().map(p => opt(`app:${p.key}`, p.label, 'Apps', p.icon)),
      opt('any', 'Whatever plays loudest'),
    ];
  });

  refreshLists(): void {
    this.integrations.refreshProcesses();
    this.integrations.outputDevices.reload();
    this.integrations.inputDevices.reload();
  }

  add(key: string): void {
    if (key) this.patch({ sources: [...this.cfg().sources, sourceOf(key)] });
    this.addRound.update(n => n + 1);
  }

  move(i: number, by: number): void {
    const sources = [...this.cfg().sources];
    const j = i + by;
    if (j < 0 || j >= sources.length) return;
    [sources[i], sources[j]] = [sources[j], sources[i]];
    this.patch({ sources });
  }

  remove(i: number): void { this.patch({ sources: this.cfg().sources.filter((_, k) => k !== i) }); }

  sourceLabel(s: VisualizerSource): string {
    const st = this.status();
    switch (s.kind) {
      case 'OUTPUT':
        return s.device ? deviceName(this.integrations.outputDevices.value(), s.device) : withName('Default output', st?.defaultOutput);
      case 'INPUT':
        return s.device ? deviceName(this.integrations.inputDevices.value(), s.device) : withName('Default input', st?.defaultInput);
      case 'APP':
        return s.app ?? '';
      default:
        return 'Whatever plays loudest';
    }
  }

  appIcon(s: VisualizerSource): string | null {
    return s.kind === 'APP' ? this.integrations.processItems().find(p => p.key === s.app)?.icon ?? null : null;
  }

  kindIcon(s: VisualizerSource): IconName {
    switch (s.kind) {
      case 'OUTPUT': return 'volume';
      case 'INPUT': return 'mic';
      case 'APP': return 'window';
      default: return 'wave';
    }
  }

  isListening(s: VisualizerSource): boolean {
    const l = this.status()?.listening;
    return !!l && sourceKey(l) === sourceKey(s);
  }

  /** "All" is the empty list; picking single lights narrows it, and removing the last goes back to all. */
  toggleLight(key: string): void {
    const lights = this.cfg().lights;
    this.patch({ lights: lights.includes(key) ? lights.filter(k => k !== key) : [...lights, key] });
  }

  isPreset(p: Preset): boolean {
    const c = this.cfg();
    if (c.style !== p.style) return false;
    if (p.style === 'RAINBOW') return true;
    const same = (a?: string, b?: string) => (a ?? '').toUpperCase() === (b ?? '').toUpperCase();
    return same(c.highColor, p.high) && (p.style === 'PULSE' || same(c.lowColor, p.low));
  }

  /** A preset changes only the look; switching it on when it was off, as picking one means wanting to see it. */
  applyPreset(p: Preset): void {
    const c = this.cfg();
    this.patch({
      style: p.style,
      lowColor: p.low ?? c.lowColor,
      highColor: p.high ?? c.highColor,
      when: c.when === 'OFF' ? 'PLAYING' : c.when,
    });
  }

  presetSwatch(p: Preset): string {
    if (p.style === 'RAINBOW') return 'linear-gradient(90deg, #FF0000, #FFD000, #00FF40, #00A0FF, #A000FF)';
    if (p.style === 'PULSE') return p.high ?? DEFAULT_HIGH;
    return `linear-gradient(90deg, ${p.low}, ${p.high})`;
  }
}

function withName(label: string, name?: string | null): string {
  return name ? `${label} (${name})` : label;
}

function deviceName(devices: { id: string; name: string }[] | undefined, id: string): string {
  return devices?.find(d => d.id === id)?.name ?? id;
}
