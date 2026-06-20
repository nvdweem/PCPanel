import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { DeviceStateService } from '../../services/device-state.service';
import { DebugService } from '../../services/debug.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { LightingConfig } from '../../models/generated/backend.types';
import { PcKnobComponent } from './pc-knob.component';
import { PcFaderComponent } from './pc-fader.component';
import { PcLogoComponent } from './pc-logo.component';
import { analogPct, controlVisual, knobColor, processNameOf, shortLabel } from './device-visual.util';

export type ControlKind = 'dial' | 'slider' | 'logo';
export interface ControlClick {
  kind: ControlKind;
  /** analog index: knob = i, Pro slider = i+5, logo = 0 */
  index: number;
  contextClicked: boolean;
  event: Event;
}

interface KnobVM { index: number; label: string; pct: number; color: string; off: boolean; selected: boolean; assign: string; anim: string; dur: string; bmin: number; }
interface FaderVM { index: number; label: string; pct: number; colors: string[]; labelColor: string; off: boolean; selected: boolean; assign: string; anim: string; dur: string; bmin: number; }

function nearBlack(hex: string): boolean {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
  if (!m) return false;
  const n = parseInt(m[1], 16);
  return (0.299 * (n >> 16 & 255) + 0.587 * (n >> 8 & 255) + 0.114 * (n & 255)) < 18;
}

/**
 * The on-screen device — the focal point of the editor. Renders the live
 * snapshot (knob positions, LED colors, animation) for Pro / Mini / RGB and
 * emits a {@link ControlClick} when a control is clicked or right-clicked.
 */
@Component({
  selector: 'pc-device',
  standalone: true,
  imports: [NgTemplateOutlet, PcKnobComponent, PcFaderComponent, PcLogoComponent],
  template: `
    @if (snap(); as s) {
      <div class="chassis" [class.flat]="flat()">
        <!-- PRO: 5 knobs (brick 2+3) + 4 sliders + logo -->
        @if (isPro()) {
          <div class="pro-knobs">
            <div class="row top">
              @for (k of knobs().slice(0, 2); track k.index) {
                <ng-container [ngTemplateOutlet]="knobTpl" [ngTemplateOutletContext]="{ $implicit: k }"></ng-container>
              }
            </div>
            <div class="row bottom">
              @for (k of knobs().slice(2); track k.index) {
                <ng-container [ngTemplateOutlet]="knobTpl" [ngTemplateOutletContext]="{ $implicit: k }"></ng-container>
              }
            </div>
          </div>
          <div class="sliders">
            @for (f of faders(); track f.index) {
              <div class="slot fader-slot" role="button" tabindex="0" [attr.aria-label]="'Configure ' + f.label"
                   (click)="emit('slider', f.index, false, $event)" (contextmenu)="emit('slider', f.index, true, $event)"
                   (keydown.enter)="emit('slider', f.index, false, $event)" (keydown.space)="emit('slider', f.index, false, $event); $event.preventDefault()">
                <span class="s-label" [style.color]="f.off ? 'var(--text-3)' : f.labelColor"
                      [style.text-shadow]="f.off ? 'none' : '0 0 9px ' + f.labelColor + 'AA'">{{ f.label }}</span>
                <pc-fader [value]="f.pct" [colors]="f.colors" [off]="f.off" [selected]="f.selected"
                          [height]="faderHeight()" [animClass]="f.anim" [animDuration]="f.dur" [breathMin]="f.bmin"></pc-fader>
                @if (showChips() && f.assign) {
                  <span class="chip">
                    @if (iconFor(f.index); as ic) { <img class="chip-ico" [src]="ic" alt=""> }
                    <span class="chip-txt">{{ f.assign }}</span>
                  </span>
                }
              </div>
            }
          </div>
          @if (isPro()) {
            <div class="logo-wrap" role="button" tabindex="0" aria-label="Configure logo lighting"
                 (click)="emit('logo', 0, false, $event)" (contextmenu)="emit('logo', 0, true, $event)"
                 (keydown.enter)="emit('logo', 0, false, $event)" (keydown.space)="emit('logo', 0, false, $event); $event.preventDefault()">
              <pc-logo [color]="logo().color" [off]="logo().off" [animClass]="logo().anim"
                       [animDuration]="logo().dur" [breathMin]="logo().bmin"></pc-logo>
            </div>
          }
        } @else {
          <!-- MINI / RGB: 4 knobs in a row -->
          <div class="row mini-row">
            @for (k of knobs(); track k.index) {
              <ng-container [ngTemplateOutlet]="knobTpl" [ngTemplateOutletContext]="{ $implicit: k }"></ng-container>
            }
          </div>
        }
      </div>
    }

    <ng-template #knobTpl let-k>
      <div class="slot knob-slot" role="button" tabindex="0" [attr.aria-label]="'Configure ' + k.label"
           (click)="emit('dial', k.index, false, $event)" (contextmenu)="emit('dial', k.index, true, $event)"
           (keydown.enter)="emit('dial', k.index, false, $event)" (keydown.space)="emit('dial', k.index, false, $event); $event.preventDefault()">
        <pc-knob [value]="k.pct" [color]="k.color" [off]="k.off" [selected]="k.selected" [size]="knobSize()"
                 [animClass]="k.anim" [animDuration]="k.dur" [breathMin]="k.bmin"></pc-knob>
        @if (showLabels()) { <span class="k-label">{{ k.label }}</span> }
        @if (showChips() && k.assign) {
          <span class="chip">
            @if (iconFor(k.index); as ic) { <img class="chip-ico" [src]="ic" alt=""> }
            <span class="chip-txt">{{ k.assign }}</span>
          </span>
        }
      </div>
    </ng-template>
  `,
  styles: [`
    :host { display: inline-block; }
    .chassis {
      background: linear-gradient(180deg, #121419, #0C0D11); border: 1px solid var(--line);
      border-radius: var(--r-chassis); padding: 30px 40px; box-shadow: var(--sh-chassis);
      display: flex; flex-direction: column; align-items: center; gap: 24px;
    }
    .chassis.flat { box-shadow: 0 18px 50px rgba(0,0,0,0.5); }
    .pro-knobs { display: flex; flex-direction: column; align-items: center; gap: 22px; width: 236px; }
    .row { display: flex; }
    .row.top { justify-content: center; gap: 31px; width: 100%; }
    .row.bottom { justify-content: space-between; width: 100%; }
    .row.mini-row { gap: 24px; }
    .sliders { display: flex; justify-content: center; gap: 30px; }
    .slot { display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer; outline: none; border-radius: 10px; }
    .slot:focus-visible, .logo-wrap:focus-visible { outline: none; box-shadow: 0 0 0 2px var(--accent-border-2); }
    .logo-wrap { outline: none; border-radius: 10px; }
    .fader-slot { gap: 9px; }
    .k-label, .s-label { font-family: var(--font-mono); font-size: 9.5px; letter-spacing: 0.04em; color: #8A909B; }
    .chip {
      display: inline-flex; align-items: center; gap: 4px;
      font-family: var(--font-ui); font-size: 9.5px; color: var(--text-soft); background: var(--raised);
      border: 1px solid var(--raised-line); padding: 2px 7px; border-radius: var(--r-pill); max-width: 66px;
    }
    .chip-ico { width: 12px; height: 12px; border-radius: 3px; object-fit: contain; flex: none; }
    .chip-txt { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .logo-wrap { cursor: pointer; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PcDeviceComponent {
  private readonly state = inject(DeviceStateService);
  private readonly debug = inject(DebugService);
  private readonly integrations = inject(IntegrationDataService);

  readonly serial = input.required<string>();
  readonly showLabels = input<boolean>(true);
  readonly showChips = input<boolean>(false);
  readonly flat = input<boolean>(false);
  readonly knobSize = input<number>(56);
  readonly faderHeight = input<number>(128);
  readonly selectedKind = input<ControlKind | null>(null);
  readonly selectedIndex = input<number>(-1);

  readonly controlClick = output<ControlClick>();

  readonly snap = this.state.snapshotFor(this.serial);
  /** Debug device-type override (Settings → Debug) wins over the real type. */
  readonly effectiveType = computed(() => this.debug.deviceTypeOverride() || this.snap()?.deviceType);
  readonly isPro = computed(() => this.effectiveType() === 'PCPANEL_PRO');

  private readonly config = computed<LightingConfig | null>(() => this.snap()?.lightingConfig ?? null);

  /** process-name (lowercased) → icon data-URI, for assignment chips. */
  private readonly processIconMap = computed(() => {
    const m = new Map<string, string>();
    for (const p of this.integrations.processes.value() ?? []) {
      if (p.name && p.icon) m.set(p.name.toLowerCase(), p.icon);
    }
    return m;
  });

  /** Icon for a control's assignment: the per-control overlay icon if set,
   *  otherwise the controlled app's process icon. Undefined if neither exists. */
  iconFor(index: number): string | undefined {
    const snap = this.snap();
    const profile = snap?.currentProfileSnapshot;
    const name = profile?.knobSettings?.[String(index)]?.overlayIcon || processNameOf(profile?.dialData?.[String(index)]);
    return name ? this.processIconMap().get(name.toLowerCase()) : undefined;
  }

  readonly knobs = computed<KnobVM[]>(() => {
    const s = this.snap();
    if (!s) return [];
    const total = this.isPro() ? 5 : 4;
    const cfg = this.config();
    const out: KnobVM[] = [];
    for (let i = 0; i < total; i++) {
      const color = knobColor(s.dialColors, i, total, cfg, '#2A2E37');
      const vis = controlVisual(color, cfg, '#2A2E37');
      const off = nearBlack(vis.fill);
      const selected = this.selectedKind() === 'dial' && this.selectedIndex() === i;
      out.push({
        index: i, label: `K${i + 1}`, pct: analogPct(s.analogValues?.[i]),
        color: vis.fill, off, selected,
        assign: shortLabel(s.currentProfileSnapshot?.dialData?.[String(i)]),
        anim: vis.animClass, dur: vis.animDuration, bmin: vis.breathMin,
      });
    }
    return out;
  });

  readonly faders = computed<FaderVM[]>(() => {
    const s = this.snap();
    if (!s || !this.isPro()) return [];
    const cfg = this.config();
    const count = s.sliderColors?.length || 4;
    const out: FaderVM[] = [];
    for (let j = 0; j < count; j++) {
      const analogIdx = j + 5;
      const segs = (s.sliderColors?.[j] ?? []).map(c => controlVisual(c, cfg, '#2A2E37').fill);
      const first = segs[0] ?? '#2A2E37';
      const vis = controlVisual(s.sliderColors?.[j]?.[0], cfg, '#2A2E37');
      const labelColor = s.sliderLabelColors?.[j] || first;
      const off = segs.every(nearBlack);
      out.push({
        index: analogIdx, label: `S${j + 1}`, pct: analogPct(s.analogValues?.[analogIdx]),
        colors: segs.length ? segs : ['#2A2E37'], labelColor,
        off, selected: this.selectedKind() === 'slider' && this.selectedIndex() === analogIdx,
        assign: shortLabel(s.currentProfileSnapshot?.dialData?.[String(analogIdx)]),
        anim: vis.animClass, dur: vis.animDuration, bmin: vis.breathMin,
      });
    }
    return out;
  });

  readonly logo = computed(() => {
    const s = this.snap();
    const cfg = this.config();
    const vis = controlVisual(s?.logoColor, cfg, '#FFB020');
    return { color: vis.fill, off: nearBlack(vis.fill), anim: vis.animClass, dur: vis.animDuration, bmin: vis.breathMin };
  });

  emit(kind: ControlKind, index: number, contextClicked: boolean, event: Event): void {
    if (contextClicked) event.preventDefault();
    this.controlClick.emit({ kind, index, contextClicked, event });
  }
}
