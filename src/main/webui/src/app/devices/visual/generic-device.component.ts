import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceCapabilitiesService } from '../../services/device-capabilities.service';
import { PcKnobComponent } from './pc-knob.component';
import { PcFaderComponent } from './pc-fader.component';
import { analogPct } from './device-visual.util';
import { ControlClick, ControlKind } from './control-click';

interface KnobVM { index: number; label: string; pct: number; color: string; }
interface FaderVM { index: number; label: string; pct: number; color: string; }
interface LightVM { index: number; label: string; color: string; }
interface OutVM { index: number; label: string; value: number; }

/**
 * Renderer for an arbitrary {@link DeviceDescriptor} that is NOT a PCPanel: a
 * grid of {@link PcKnobComponent} for each KNOB/ENCODER analog input and
 * {@link PcFaderComponent} for each SLIDER (value normalized via the input's
 * source range), generic colored swatches for the device's light outputs, and a
 * numeric readout for each analog output. Emits the same {@link ControlClick}
 * contract as {@link PcDeviceComponent} so command editing works unchanged. It
 * deliberately has no PCPanel wordmark/logo.
 */
@Component({
  selector: 'pc-generic-device',
  standalone: true,
  imports: [PcKnobComponent, PcFaderComponent],
  template: `
    @if (snap(); as s) {
      <div class="chassis" [class.flat]="flat()">
        @if (knobs().length) {
          <div class="group">
            <div class="grid knob-grid">
              @for (k of knobs(); track k.index) {
                <div class="slot" role="button" tabindex="0" [attr.aria-label]="'Configure ' + k.label"
                     (click)="emit('dial', k.index, false, $event)" (contextmenu)="emit('dial', k.index, true, $event)"
                     (keydown.enter)="emit('dial', k.index, false, $event)" (keydown.space)="emit('dial', k.index, false, $event); $event.preventDefault()">
                  <pc-knob [value]="k.pct" [color]="k.color" [size]="knobSize()"></pc-knob>
                  @if (showLabels()) { <span class="lbl">{{ k.label }}</span> }
                </div>
              }
            </div>
          </div>
        }

        @if (faders().length) {
          <div class="group">
            <div class="grid fader-grid">
              @for (f of faders(); track f.index) {
                <div class="slot" role="button" tabindex="0" [attr.aria-label]="'Configure ' + f.label"
                     (click)="emit('slider', f.index, false, $event)" (contextmenu)="emit('slider', f.index, true, $event)"
                     (keydown.enter)="emit('slider', f.index, false, $event)" (keydown.space)="emit('slider', f.index, false, $event); $event.preventDefault()">
                  <pc-fader [value]="f.pct" [colors]="[f.color]" [height]="faderHeight()"></pc-fader>
                  @if (showLabels()) { <span class="lbl">{{ f.label }}</span> }
                </div>
              }
            </div>
          </div>
        }

        @if (lights().length) {
          <div class="group">
            <div class="micro">LIGHTS</div>
            <div class="swatches">
              @for (l of lights(); track l.index) {
                <span class="swatch" [style.background]="l.color" [title]="l.label"></span>
              }
            </div>
          </div>
        }

        @if (outputs().length) {
          <div class="group">
            <div class="micro">OUTPUTS</div>
            <div class="outs">
              @for (o of outputs(); track o.index) {
                <span class="out"><span class="out-lbl">{{ o.label }}</span><span class="out-val mono">{{ o.value }}</span></span>
              }
            </div>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    :host { display: inline-block; }
    .chassis {
      background: linear-gradient(180deg, #121419, #0C0D11); border: 1px solid var(--line);
      border-radius: var(--r-chassis); padding: 28px 36px; box-shadow: var(--sh-chassis);
      display: flex; flex-direction: column; align-items: stretch; gap: 22px;
    }
    .chassis.flat { box-shadow: 0 18px 50px rgba(0,0,0,0.5); }
    .group { display: flex; flex-direction: column; gap: 10px; }
    .grid { display: flex; flex-wrap: wrap; justify-content: center; gap: 24px; }
    .slot { display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer; outline: none; border-radius: 10px; }
    .slot:focus-visible { box-shadow: 0 0 0 2px var(--accent-border-2); }
    .lbl { font-family: var(--font-mono); font-size: 9.5px; letter-spacing: 0.04em; color: #8A909B; }
    .micro { font-family: var(--font-mono); font-size: 9px; letter-spacing: .12em; color: var(--text-3); }
    .swatches { display: flex; flex-wrap: wrap; gap: 8px; }
    .swatch { width: 18px; height: 18px; border-radius: 5px; border: 1px solid var(--line); }
    .outs { display: flex; flex-wrap: wrap; gap: 12px; }
    .out { display: inline-flex; align-items: center; gap: 6px; background: var(--raised); border: 1px solid var(--raised-line); padding: 3px 9px; border-radius: var(--r-pill); }
    .out-lbl { font-size: 10px; color: var(--text-soft); }
    .out-val { font-size: 11px; color: var(--text-2); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GenericDeviceComponent {
  private readonly state = inject(DeviceStateService);
  private readonly capsService = inject(DeviceCapabilitiesService);

  readonly serial = input.required<string>();
  readonly showLabels = input<boolean>(true);
  readonly flat = input<boolean>(false);
  readonly knobSize = input<number>(56);
  readonly faderHeight = input<number>(128);

  readonly controlClick = output<ControlClick>();

  readonly snap = this.state.snapshotFor(this.serial);
  private readonly caps = this.capsService.forSerial(this.serial);

  readonly knobs = computed<KnobVM[]>(() => {
    const s = this.snap();
    if (!s) return [];
    return this.caps.knobs().map(a => ({
      index: a.index, label: a.label,
      // Snapshot analogValues are already normalized to 0–255 for every provider,
      // so render against the canonical domain, not the raw sourceMin/sourceMax.
      pct: analogPct(s.analogValues?.[a.index]),
      color: s.dialColors?.[a.index] ?? '#7AA2FF',
    }));
  });

  readonly faders = computed<FaderVM[]>(() => {
    const s = this.snap();
    if (!s) return [];
    return this.caps.sliders().map((a, pos) => ({
      index: a.index, label: a.label,
      pct: analogPct(s.analogValues?.[a.index]),
      color: s.sliderColors?.[pos]?.[0] ?? '#7AA2FF',
    }));
  });

  readonly lights = computed<LightVM[]>(() => {
    const s = this.snap();
    return this.caps.lightOutputs().map(l => ({
      index: l.index, label: l.label,
      color: this.lightColor(l.group, l.index, s) ?? '#2A2E37',
    }));
  });

  readonly outputs = computed<OutVM[]>(() => {
    const s = this.snap();
    return this.caps.analogOutputs().map(o => ({
      index: o.index, label: o.label, value: s?.analogValues?.[o.index] ?? 0,
    }));
  });

  private lightColor(group: string, index: number, s: ReturnType<typeof this.snap>): string | undefined {
    if (!s) return undefined;
    if (group === 'LOGO') return s.logoColor;
    if (group === 'SLIDER' || group === 'SLIDER_LABEL') return s.sliderColors?.[index]?.[0];
    return s.dialColors?.[index];
  }

  emit(kind: ControlKind, index: number, contextClicked: boolean, event: Event): void {
    if (contextClicked) event.preventDefault();
    this.controlClick.emit({ kind, index, contextClicked, event });
  }
}
