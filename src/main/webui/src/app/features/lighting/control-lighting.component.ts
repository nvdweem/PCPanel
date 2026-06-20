import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import {
  LightingConfig, SingleKnobLightingConfig, SingleSliderLabelLightingConfig, SingleSliderLightingConfig,
} from '../../models/generated/backend.types';
import { ColorPickerComponent, SelectComponent, SelectOption, ToastService } from '../../ui';
import { normalizeLogo } from './lighting-util';
import { DeviceCapabilitiesService } from '../../services/device-capabilities.service';

const BLACK = '#000000';
const isBlackHex = (c: string | undefined): boolean => !c || /^#?0{3,8}$/i.test(c.trim());
const KNOB_DEFAULT: SingleKnobLightingConfig = { mode: 'STATIC', color1: '#FFB020', color2: '#000000' };
const SLIDER_DEFAULT: SingleSliderLightingConfig = { mode: 'STATIC', color1: '#FFB020', color2: '#000000', muteOverrideColor: '#000000', muteOverrideDeviceOrFollow: '' };
const LABEL_DEFAULT: SingleSliderLabelLightingConfig = { mode: 'STATIC', color: '#FFB020', muteOverrideColor: '#000000', muteOverrideDeviceOrFollow: '' };

/**
 * Per-control lighting editor for a SINGLE control (knob, or slider + its label),
 * shown in the action-assignment rail when the device is in CUSTOM lighting mode.
 * Owns a debounced save of the whole LightingConfig (only one instance is mounted
 * at a time, so there's no cross-instance race). "Off" writes STATIC black so the
 * LED actually turns off on the device; the slider label can "follow" its slider.
 */
@Component({
  selector: 'pc-control-lighting',
  standalone: true,
  imports: [SelectComponent, ColorPickerComponent],
  template: `
    @if (config(); as cfg) {
      <div class="cl">
        @if (!isSlider()) {
          <!-- Knob -->
          <div class="row">
            <pc-select [options]="knobModes" [value]="knobUi()" [block]="true" (valueChange)="setKnobUi($any($event))"></pc-select>
          </div>
          @if (knobUi() !== 'off') {
            <pc-color-picker label="Color" [value]="knob().color1" (valueChange)="setKnob('color1', $event)"></pc-color-picker>
            @if (knobUi() === 'gradient') {
              <pc-color-picker label="Color 2" [value]="knob().color2" (valueChange)="setKnob('color2', $event)"></pc-color-picker>
            }
            <pc-color-picker label="Muted" [value]="knob().muteOverrideColor || '#000000'" (valueChange)="setKnob('muteOverrideColor', $event)"></pc-color-picker>
          }
        } @else {
          <!-- Slider -->
          <div class="grp-label">SLIDER</div>
          <div class="row">
            <pc-select [options]="sliderModes" [value]="sliderUi()" [block]="true" (valueChange)="setSliderUi($any($event))"></pc-select>
          </div>
          @if (sliderUi() !== 'off') {
            <pc-color-picker label="Color" [value]="slider().color1" (valueChange)="setSlider('color1', $event)"></pc-color-picker>
            @if (sliderUi() === 'static-gradient' || sliderUi() === 'gradient') {
              <pc-color-picker label="Color 2" [value]="slider().color2" (valueChange)="setSlider('color2', $event)"></pc-color-picker>
            }
            <pc-color-picker label="Muted" [value]="slider().muteOverrideColor || '#000000'" (valueChange)="setSlider('muteOverrideColor', $event)"></pc-color-picker>
          }
          <div class="grp-label">LABEL</div>
          <div class="row">
            <pc-select [options]="labelModes" [value]="labelUi()" [block]="true" (valueChange)="setLabelUi($any($event))"></pc-select>
          </div>
          @if (labelUi() === 'static') {
            <pc-color-picker label="Color" [value]="label().color" (valueChange)="setLabel('color', $event)"></pc-color-picker>
          }
          @if (labelUi() !== 'off') {
            <pc-color-picker label="Muted" [value]="label().muteOverrideColor || '#000000'" (valueChange)="setLabel('muteOverrideColor', $event)"></pc-color-picker>
          }
        }
      </div>
    }
  `,
  styles: [`
    .cl { display: flex; flex-direction: column; gap: 8px; }
    .row { display: flex; }
    .grp-label { font-family: var(--font-mono); font-size: 9px; letter-spacing: .12em; color: var(--text-3); margin-top: 4px; }
    .cline { display: flex; align-items: center; justify-content: space-between; background: #121419; border: 1px solid var(--line); border-radius: var(--r-sm); padding: 6px 10px; }
    .lbl { font-size: 11.5px; color: var(--text-2); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlLightingComponent {
  private readonly state = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly toast = inject(ToastService);
  private readonly capsService = inject(DeviceCapabilitiesService);

  readonly serial = input.required<string>();
  readonly index = input.required<number>();      // analog index
  readonly isSlider = input<boolean>(false);

  private readonly snap = this.state.snapshotFor(this.serial);
  private readonly caps = this.capsService.forSerial(this.serial);
  /** Position of this slider among the device's sliders (Pro idx 5 → 0). */
  private readonly sliderIdx = computed(() => this.caps.sliderNumber(this.index()));

  readonly config = signal<LightingConfig | null>(null);
  private loadedKey = '';
  private timer?: ReturnType<typeof setTimeout>;

  readonly knobModes: SelectOption[] = [
    { value: 'off', label: 'Off' }, { value: 'static', label: 'Static color' }, { value: 'gradient', label: 'Volume gradient' },
  ];
  readonly sliderModes: SelectOption[] = [
    { value: 'off', label: 'Off' }, { value: 'static', label: 'Static color' },
    { value: 'static-gradient', label: 'Static gradient' }, { value: 'gradient', label: 'Volume gradient' },
  ];
  readonly labelModes: SelectOption[] = [
    { value: 'off', label: 'Off' }, { value: 'follow', label: 'Follow slider' }, { value: 'static', label: 'Static color' },
  ];

  constructor() {
    effect(() => {
      const s = this.snap();
      const key = `${this.serial()}:${this.index()}`;
      if (!s || key === this.loadedKey) return;
      this.loadedKey = key;
      untracked(() => this.config.set(JSON.parse(JSON.stringify(s.lightingConfig))));
    });
  }

  private patch(part: Partial<LightingConfig>): void {
    const cur = this.config();
    if (!cur) return;
    this.config.set({ ...cur, ...part });
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      const cfg = this.config();
      if (cfg) this.deviceService.setLighting(this.serial(), normalizeLogo(cfg)).subscribe({ error: () => this.toast.show('Could not save lighting', { kind: 'error' }) });
    }, 350);
  }

  // ── knob ──────────────────────────────────────────────────────────────────
  private padKnobs(len: number): SingleKnobLightingConfig[] {
    const arr = [...(this.config()?.knobConfigs ?? [])];
    while (arr.length < len) arr.push({ ...KNOB_DEFAULT });
    return arr;
  }
  knob(): SingleKnobLightingConfig { return this.config()?.knobConfigs?.[this.index()] ?? KNOB_DEFAULT; }
  knobUi(): string { const c = this.knob(); if (c.mode === 'NONE' || (c.mode === 'STATIC' && isBlackHex(c.color1))) return 'off'; return c.mode === 'VOLUME_GRADIENT' ? 'gradient' : 'static'; }
  setKnobUi(ui: string): void {
    const arr = this.padKnobs(this.index() + 1); const cur = arr[this.index()];
    if (ui === 'off') arr[this.index()] = { ...cur, mode: 'STATIC', color1: BLACK, color2: BLACK };
    else if (ui === 'gradient') arr[this.index()] = { ...cur, mode: 'VOLUME_GRADIENT', color1: isBlackHex(cur.color1) ? '#FFB020' : cur.color1, color2: isBlackHex(cur.color2) ? '#3B6BFF' : cur.color2 };
    else arr[this.index()] = { ...cur, mode: 'STATIC', color1: isBlackHex(cur.color1) ? '#FFB020' : cur.color1 };
    this.patch({ knobConfigs: arr });
  }
  setKnob<K extends keyof SingleKnobLightingConfig>(key: K, value: SingleKnobLightingConfig[K]): void {
    const arr = this.padKnobs(this.index() + 1);
    arr[this.index()] = { ...arr[this.index()], [key]: value };
    this.patch({ knobConfigs: arr });
  }

  // ── slider ────────────────────────────────────────────────────────────────
  private padSliders(len: number): SingleSliderLightingConfig[] {
    const arr = [...(this.config()?.sliderConfigs ?? [])];
    while (arr.length < len) arr.push({ ...SLIDER_DEFAULT });
    return arr;
  }
  slider(): SingleSliderLightingConfig { return this.config()?.sliderConfigs?.[this.sliderIdx()] ?? SLIDER_DEFAULT; }
  sliderUi(): string { const c = this.slider(); if (c.mode === 'NONE' || (c.mode === 'STATIC' && isBlackHex(c.color1))) return 'off'; return c.mode === 'STATIC_GRADIENT' ? 'static-gradient' : c.mode === 'VOLUME_GRADIENT' ? 'gradient' : 'static'; }
  setSliderUi(ui: string): void {
    const j = this.sliderIdx(); const arr = this.padSliders(j + 1); const cur = arr[j];
    const c1 = isBlackHex(cur.color1) ? '#FFB020' : cur.color1; const c2 = isBlackHex(cur.color2) ? '#3B6BFF' : cur.color2;
    if (ui === 'off') arr[j] = { ...cur, mode: 'STATIC', color1: BLACK, color2: BLACK };
    else if (ui === 'static-gradient') arr[j] = { ...cur, mode: 'STATIC_GRADIENT', color1: c1, color2: c2 };
    else if (ui === 'gradient') arr[j] = { ...cur, mode: 'VOLUME_GRADIENT', color1: c1, color2: c2 };
    else arr[j] = { ...cur, mode: 'STATIC', color1: c1 };
    this.patch({ sliderConfigs: arr });
  }
  setSlider<K extends keyof SingleSliderLightingConfig>(key: K, value: SingleSliderLightingConfig[K]): void {
    const j = this.sliderIdx(); const arr = this.padSliders(j + 1);
    arr[j] = { ...arr[j], [key]: value };
    const part: Partial<LightingConfig> = { sliderConfigs: arr };
    if (key === 'color1' && this.labelUi() === 'follow') {
      const lArr = this.padLabels(j + 1); lArr[j] = { ...lArr[j], mode: 'STATIC', color: value as string };
      part.sliderLabelConfigs = lArr;
    }
    this.patch(part);
  }

  // ── slider label ──────────────────────────────────────────────────────────
  private padLabels(len: number): SingleSliderLabelLightingConfig[] {
    const arr = [...(this.config()?.sliderLabelConfigs ?? [])];
    while (arr.length < len) arr.push({ ...LABEL_DEFAULT, mode: 'STATIC', color: this.slider().color1 || '#FFB020' });
    return arr;
  }
  label(): SingleSliderLabelLightingConfig { return this.config()?.sliderLabelConfigs?.[this.sliderIdx()] ?? { ...LABEL_DEFAULT, mode: 'STATIC', color: this.slider().color1 || '#FFB020' }; }
  labelUi(): string {
    const c = this.config()?.sliderLabelConfigs?.[this.sliderIdx()];
    if (!c || c.mode === 'NONE') return this.sliderIdx() < (this.config()?.sliderLabelConfigs?.length ?? 0) ? 'off' : 'follow';
    if (c.mode === 'STATIC' && isBlackHex(c.color)) return 'off';
    if (c.mode === 'STATIC' && c.color === this.slider().color1) return 'follow';
    return 'static';
  }
  setLabelUi(ui: string): void {
    const j = this.sliderIdx(); const arr = this.padLabels(j + 1); const cur = arr[j];
    if (ui === 'off') arr[j] = { ...cur, mode: 'STATIC', color: BLACK };
    else if (ui === 'follow') arr[j] = { ...cur, mode: 'STATIC', color: this.slider().color1 || '#FFB020' };
    else arr[j] = { ...cur, mode: 'STATIC', color: isBlackHex(cur.color) ? '#FFB020' : cur.color };
    this.patch({ sliderLabelConfigs: arr });
  }
  setLabel<K extends keyof SingleSliderLabelLightingConfig>(key: K, value: SingleSliderLabelLightingConfig[K]): void {
    const j = this.sliderIdx(); const arr = this.padLabels(j + 1);
    arr[j] = { ...arr[j], [key]: value };
    this.patch({ sliderLabelConfigs: arr });
  }
}
