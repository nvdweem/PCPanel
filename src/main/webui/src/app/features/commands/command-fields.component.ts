import { ChangeDetectionStrategy, Component, computed, inject, input, model } from '@angular/core';
import { OverlayModule } from '@angular/cdk/overlay';
import { CommandDef, FieldDef, LiveSource } from './command-catalog';
import { IntegrationDataService } from './integration-data.service';
import {
  AppPickerComponent, IconComponent, KeyRecorderComponent, SegmentedComponent,
  SelectComponent, SelectOption, ToggleComponent,
} from '../../ui';

type Cmd = Record<string, any>;

/**
 * Generic editor for one command. Renders the command's `fields` from the
 * catalog and two-way binds [(command)]. Dial commands also get the input-mapping
 * editor (invert + start/end with a curve preview), bound to `command.dialParams`.
 */
@Component({
  selector: 'pc-command-fields',
  standalone: true,
  imports: [OverlayModule, IconComponent, ToggleComponent, SelectComponent, AppPickerComponent, KeyRecorderComponent, SegmentedComponent],
  template: `
    <div class="fields">
      @for (f of def().fields; track f.kind + ($any(f).key || '')) {
        @switch (f.kind) {
          @case ('text') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <input class="pc-input" [class.mono]="$any(f).mono" [placeholder]="$any(f).placeholder || ''"
                     [value]="val($any(f).key)" (input)="set($any(f).key, $any($event.target).value)">
            </div>
          }
          @case ('number') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <input class="pc-input mono" type="number" [min]="$any(f).min" [max]="$any(f).max"
                     [value]="val($any(f).key)" (input)="set($any(f).key, +$any($event.target).value)">
            </div>
          }
          @case ('toggle') {
            <div class="row-between">
              <span class="rlabel">{{ $any(f).label }}</span>
              <pc-toggle [value]="!!val($any(f).key)" (valueChange)="set($any(f).key, $event)"></pc-toggle>
            </div>
          }
          @case ('select') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="$any(f).options" [value]="val($any(f).key)"
                         (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('mute') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="muteOpts" [value]="val($any(f).key)"
                         (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('select-live') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="liveOptions($any(f).source)" [value]="val($any(f).key)"
                         placeholder="—" (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('device') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="deviceOptions($any(f).filter)" [value]="val($any(f).key)"
                         placeholder="—" (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('apps') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <div class="chips">
                @for (p of asArray($any(f).key); track p) {
                  <span class="pc-chip">{{ p }}<span class="x" (click)="removeFromArray($any(f).key, p)"><pc-icon name="x" [size]="11" [strokeWidth]="2.5"></pc-icon></span></span>
                }
                <button class="pc-chip dashed" cdkOverlayOrigin #ao="cdkOverlayOrigin" (click)="appsOpen.set($any(f).key)">
                  <pc-icon name="plus" [size]="12"></pc-icon> add app
                </button>
                <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="ao" [cdkConnectedOverlayOpen]="appsOpen() === $any(f).key"
                             [cdkConnectedOverlayHasBackdrop]="true" cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                             [cdkConnectedOverlayWidth]="300" [cdkConnectedOverlayOffsetY]="6"
                             (backdropClick)="appsOpen.set(null)" (detach)="appsOpen.set(null)">
                  <pc-app-picker [items]="data.processItems()" [multi]="true"
                                 [value]="asArray($any(f).key)" (valueChange)="set($any(f).key, $event)"></pc-app-picker>
                </ng-template>
              </div>
            </div>
          }
          @case ('devices-list') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-app-picker [items]="data.deviceItems()" [multi]="true"
                             [value]="asArray($any(f).key)" (valueChange)="set($any(f).key, $event)"
                             placeholder="Search devices…"></pc-app-picker>
            </div>
          }
          @case ('keystroke') {
            <div class="field-block">
              <div class="flabel">Mode</div>
              <pc-segmented [options]="keyModes" [value]="val('type')" (valueChange)="set('type', $event)"></pc-segmented>
              <div style="height:10px"></div>
              @if (val('type') === 'TEXT') {
                <input class="pc-input mono" placeholder="Text to type…" [value]="val('text')" (input)="set('text', $any($event.target).value)">
              } @else {
                <pc-key-recorder [value]="val('keystroke')" (valueChange)="set('keystroke', $event)"></pc-key-recorder>
              }
            </div>
          }
        }
      }

      @if (showMapping() && command()['dialParams']) {
        <div class="mapping">
          <div class="flabel">Input mapping</div>
          <div class="map-row">
            <svg width="150" height="100" viewBox="0 0 150 100">
              <rect x="1" y="1" width="148" height="98" rx="8" fill="#0E0F12" stroke="#23262E"></rect>
              <line x1="18" y1="82" x2="132" y2="82" stroke="#2A2E37" stroke-width="1"></line>
              <line x1="18" y1="18" x2="18" y2="82" stroke="#2A2E37" stroke-width="1"></line>
              <path [attr.d]="curvePath()" fill="none" stroke="#FFB020" stroke-width="2.5" stroke-linecap="round"></path>
              <circle [attr.cx]="18" [attr.cy]="curveY0()" r="3.5" fill="#FFB020"></circle>
              <circle [attr.cx]="132" [attr.cy]="curveY1()" r="3.5" fill="#FFB020"></circle>
              <text x="14" y="95" font-family="monospace" font-size="8" fill="#5F6671">start</text>
              <text x="112" y="95" font-family="monospace" font-size="8" fill="#5F6671">end</text>
            </svg>
            <div class="map-controls">
              <div class="row-between">
                <span class="rlabel">Invert direction</span>
                <pc-toggle [value]="!!command()['dialParams'].invert" (valueChange)="setDial('invert', $event)"></pc-toggle>
              </div>
              <div class="map-fields">
                <div class="pc-field">
                  <div class="pc-field-label">Start</div>
                  <input class="pc-field-input" type="number" min="0" max="100"
                         [value]="startDisplay()" (input)="setStart(+$any($event.target).value)">
                </div>
                <div class="pc-field">
                  <div class="pc-field-label">End</div>
                  <input class="pc-field-input" type="number" min="0" max="100"
                         [value]="endDisplay()" (input)="setEnd(+$any($event.target).value)">
                </div>
              </div>
            </div>
          </div>
        </div>
      }

      @if (!def().fields.length && !(showMapping() && command()['dialParams'])) {
        <div class="no-fields">No options for this action.</div>
      }
    </div>
  `,
  styles: [`
    .fields { display: flex; flex-direction: column; gap: 16px; }
    .field-block { display: flex; flex-direction: column; }
    .flabel { font-size: 11.5px; color: var(--text-2); margin-bottom: 8px; }
    .row-between { display: flex; align-items: center; justify-content: space-between; }
    .rlabel { font-size: 12.5px; color: var(--text-soft); }
    .chips { display: flex; gap: 8px; flex-wrap: wrap; }
    .mapping { border-top: 1px solid var(--line-hair); padding-top: 16px; }
    .map-row { display: flex; gap: 18px; align-items: flex-start; }
    .map-controls { flex: 1; display: flex; flex-direction: column; gap: 11px; padding-top: 8px; }
    .map-fields { display: flex; gap: 8px; }
    .map-fields .pc-field { flex: 1; }
    .no-fields { font-size: 12.5px; color: var(--text-3); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandFieldsComponent {
  readonly data = inject(IntegrationDataService);

  readonly def = input.required<CommandDef>();
  readonly command = model.required<Cmd>();
  readonly showMapping = input<boolean>(false);
  readonly profiles = input<string[]>([]);

  readonly appsOpen = model<string | null>(null);

  readonly muteOpts: SelectOption[] = [
    { value: 'toggle', label: 'Toggle' }, { value: 'mute', label: 'Mute' }, { value: 'unmute', label: 'Unmute' },
  ];
  readonly keyModes = [{ value: 'KEY', label: 'Key combo' }, { value: 'TEXT', label: 'Type text' }];

  val(key: string): any { return this.command()[key]; }
  asArray(key: string): string[] { const v = this.command()[key]; return Array.isArray(v) ? v : []; }

  set(key: string, value: any): void {
    this.command.update(c => ({ ...c, [key]: value }));
  }
  setDial(key: string, value: any): void {
    this.command.update(c => ({ ...c, dialParams: { ...(c['dialParams'] ?? {}), [key]: value } }));
  }
  removeFromArray(key: string, item: string): void {
    this.set(key, this.asArray(key).filter(x => x !== item));
  }

  liveOptions(source: LiveSource): SelectOption[] {
    switch (source) {
      case 'obs-scenes': return strOpts(this.data.obsScenes.value());
      case 'obs-sources': return strOpts(this.data.obsSources.value());
      case 'vm-advanced': return (this.data.vmAdvanced.value() ?? []).flatMap(g => g.params.map(p => ({ value: p, label: p })));
      case 'wl-channels': return wlOpts(this.data.wlChannels());
      case 'wl-inputs': return wlOpts(this.data.wlInputs());
      case 'wl-mixes': return wlOpts(this.data.wlMixes());
      case 'wl-outputs': return wlOpts(this.data.wlOutputs());
      case 'profiles': return this.profiles().map(p => ({ value: p, label: p }));
      default: return [];
    }
  }

  deviceOptions(filter: 'output' | 'input' | 'all' | undefined): SelectOption[] {
    const res = filter === 'output' ? this.data.outputDevices : filter === 'input' ? this.data.inputDevices : this.data.audioDevices;
    return (res.value() ?? []).map(d => ({ value: d.id, label: d.name }));
  }

  // ── input mapping (Start/End as displayed positions 0..100) ──────────────────
  // Backend stores moveStart (position from bottom) and moveEnd (amount trimmed
  // from the top). The user-facing "End" is the end *position* = 100 - moveEnd,
  // so the default (moveStart 0, moveEnd 0) reads as Start 0 / End 100 = full range.
  readonly startDisplay = computed(() => clamp(this.command()['dialParams']?.moveStart ?? 0));
  readonly endDisplay = computed(() => clamp(100 - (this.command()['dialParams']?.moveEnd ?? 0)));
  private invert = computed(() => !!this.command()['dialParams']?.invert);

  setStart(v: number): void { this.setDial('moveStart', clamp(v)); }
  setEnd(v: number): void { this.setDial('moveEnd', clamp(100 - clamp(v))); }

  // Transfer curve on a [0..100] input(x) → output(y) graph, clamped outside Start/End.
  private xFor(pct: number): number { return 18 + (clamp(pct) / 100) * 114; }   // 18..132
  private yFor(pct: number): number { return 82 - (clamp(pct) / 100) * 58; }    // 82(0%)..24(100%)
  curveY0(): number { return this.invert() ? this.yFor(100) : this.yFor(0); }
  curveY1(): number { return this.invert() ? this.yFor(0) : this.yFor(100); }
  curvePath(): string {
    const s = this.startDisplay(), e = Math.max(this.startDisplay() + 0.01, this.endDisplay());
    const lo = this.invert() ? this.yFor(100) : this.yFor(0);
    const hi = this.invert() ? this.yFor(0) : this.yFor(100);
    return `M 18 ${lo} L ${this.xFor(s)} ${lo} L ${this.xFor(e)} ${hi} L 132 ${hi}`;
  }
}

function clamp(v: number): number { return Math.max(0, Math.min(100, v ?? 0)); }
function strOpts(arr: string[] | undefined): SelectOption[] { return (arr ?? []).map(s => ({ value: s, label: s })); }
function wlOpts(arr: { id: string; name?: string }[]): SelectOption[] { return arr.map(i => ({ value: i.id, label: i.name ?? i.id })); }
