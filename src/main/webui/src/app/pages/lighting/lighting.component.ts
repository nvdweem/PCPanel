import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import {
  ColorPickerComponent, IconComponent, IconName, SegmentedComponent, SegmentOption, SelectComponent,
  SelectOption, SliderComponent, StatusDotComponent, ToastService, ToggleComponent,
} from '../../ui';
import { PcDeviceComponent } from '../../devices/visual/pc-device.component';
import {
  LightingConfig, LightingMode, SingleKnobLightingConfig, SingleLogoLightingConfig,
  SingleSliderLabelLightingConfig, SingleSliderLightingConfig, SINGLE_KNOB_MODE, SINGLE_LOGO_MODE,
  SINGLE_SLIDER_LABEL_MODE, SINGLE_SLIDER_MODE,
} from '../../models/generated/backend.types';

interface ModeChip { value: LightingMode; label: string; swatch: 'solid' | 'rainbow' | 'wave' | 'breath' | 'custom'; span2?: boolean; }

const KNOB_DEFAULT: SingleKnobLightingConfig = { mode: 'STATIC', color1: '#FFB020', color2: '#000000' };
const SLIDER_DEFAULT: SingleSliderLightingConfig = { mode: 'STATIC', color1: '#FFB020', color2: '#000000', muteOverrideColor: '#000000', muteOverrideDeviceOrFollow: '' };
const SLIDER_LABEL_DEFAULT: SingleSliderLabelLightingConfig = { mode: 'STATIC', color: '#FFB020', muteOverrideColor: '#000000', muteOverrideDeviceOrFollow: '' };
const LOGO_DEFAULT: SingleLogoLightingConfig = { mode: 'STATIC', color: '#FFB020', brightness: 255, hue: 0, speed: 128 };

@Component({
  selector: 'app-lighting',
  standalone: true,
  imports: [
    IconComponent, StatusDotComponent, SliderComponent, ToggleComponent, SegmentedComponent,
    SelectComponent, ColorPickerComponent, PcDeviceComponent,
  ],
  templateUrl: './lighting.component.html',
  styleUrl: './lighting.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LightingComponent {
  private readonly state = inject(DeviceStateService);
  private readonly deviceService = inject(DeviceService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly serial = input.required<string>();

  readonly snapshot = this.state.snapshotFor(this.serial);

  /** Local editable copy, seeded once per serial from the snapshot. */
  readonly config = signal<LightingConfig | null>(null);
  private loadedKey = '';
  private saveTimer?: ReturnType<typeof setTimeout>;

  // ── mode chips ─────────────────────────────────────────────────────────────
  readonly modeChips: ModeChip[] = [
    { value: 'ALL_COLOR', label: 'Solid', swatch: 'solid' },
    { value: 'ALL_RAINBOW', label: 'Rainbow', swatch: 'rainbow' },
    { value: 'ALL_WAVE', label: 'Wave', swatch: 'wave' },
    { value: 'ALL_BREATH', label: 'Breath', swatch: 'breath' },
    { value: 'CUSTOM', label: 'Per-control / custom', swatch: 'custom', span2: true },
  ];

  readonly orientationOptions: SegmentOption[] = [
    { value: 'h', label: 'Horizontal' },
    { value: 'v', label: 'Vertical' },
  ];

  readonly knobModeOptions: SelectOption<SINGLE_KNOB_MODE>[] = [
    { value: 'NONE', label: 'Off' },
    { value: 'STATIC', label: 'Static color' },
    { value: 'VOLUME_GRADIENT', label: 'Volume gradient' },
  ];
  readonly sliderModeOptions: SelectOption<SINGLE_SLIDER_MODE>[] = [
    { value: 'NONE', label: 'Off' },
    { value: 'STATIC', label: 'Static color' },
    { value: 'STATIC_GRADIENT', label: 'Static gradient' },
    { value: 'VOLUME_GRADIENT', label: 'Volume gradient' },
  ];
  readonly sliderLabelModeOptions: SelectOption<SINGLE_SLIDER_LABEL_MODE>[] = [
    { value: 'NONE', label: 'Off' },
    { value: 'STATIC', label: 'Static color' },
  ];
  readonly logoModeOptions: SelectOption<SINGLE_LOGO_MODE>[] = [
    { value: 'NONE', label: 'Off' },
    { value: 'STATIC', label: 'Static color' },
    { value: 'RAINBOW', label: 'Rainbow' },
    { value: 'BREATH', label: 'Breath' },
  ];

  constructor() {
    effect(() => {
      const s = this.snapshot();
      const key = this.serial();
      if (!s) return;
      // Seed once per serial; don't re-seed from WS echoes while editing.
      if (key === this.loadedKey) return;
      this.loadedKey = key;
      untracked(() => this.config.set(clone(s.lightingConfig)));
    });
  }

  // ── device topology ────────────────────────────────────────────────────────
  readonly isPro = computed(() => this.snapshot()?.deviceType === 'PCPANEL_PRO');
  readonly knobCount = computed(() => this.isPro() ? 5 : 4);
  readonly knobIndexes = computed(() => Array.from({ length: this.knobCount() }, (_, i) => i));
  readonly sliderIndexes = [0, 1, 2, 3];

  // ── derived ────────────────────────────────────────────────────────────────
  readonly mode = computed<LightingMode>(() => this.config()?.lightingMode ?? 'ALL_COLOR');
  readonly isCustom = computed(() => this.mode() === 'CUSTOM');

  // ── generic immutable patch ─────────────────────────────────────────────────
  private patch(part: Partial<LightingConfig>): void {
    const cur = this.config();
    if (!cur) return;
    this.config.set({ ...cur, ...part });
    this.save();
  }

  set<K extends keyof LightingConfig>(key: K, value: LightingConfig[K]): void {
    this.patch({ [key]: value } as Partial<LightingConfig>);
  }

  setMode(value: LightingMode): void { this.patch({ lightingMode: value }); }

  /** 0|1 numeric flag helpers. */
  flagOn(v: number | undefined): boolean { return v === 1; }
  setFlag<K extends keyof LightingConfig>(key: K, on: boolean): void {
    this.set(key, (on ? 1 : 0) as LightingConfig[K]);
  }

  setOrientation(v: string | undefined): void { this.set('rainbowVertical', v === 'v' ? 1 : 0); }
  readonly orientation = computed(() => (this.config()?.rainbowVertical === 1 ? 'v' : 'h'));

  // ── per-control: knobs ───────────────────────────────────────────────────────
  private padKnobs(len: number): SingleKnobLightingConfig[] {
    const arr = [...(this.config()?.knobConfigs ?? [])];
    while (arr.length < len) arr.push({ ...KNOB_DEFAULT });
    return arr;
  }
  knobAt(i: number): SingleKnobLightingConfig { return this.config()?.knobConfigs?.[i] ?? KNOB_DEFAULT; }
  setKnob<K extends keyof SingleKnobLightingConfig>(i: number, key: K, value: SingleKnobLightingConfig[K]): void {
    const arr = this.padKnobs(i + 1);
    arr[i] = { ...arr[i], [key]: value };
    this.patch({ knobConfigs: arr });
  }

  // ── per-control: sliders ──────────────────────────────────────────────────────
  private padSliders(len: number): SingleSliderLightingConfig[] {
    const arr = [...(this.config()?.sliderConfigs ?? [])];
    while (arr.length < len) arr.push({ ...SLIDER_DEFAULT });
    return arr;
  }
  sliderAt(i: number): SingleSliderLightingConfig { return this.config()?.sliderConfigs?.[i] ?? SLIDER_DEFAULT; }
  setSlider<K extends keyof SingleSliderLightingConfig>(i: number, key: K, value: SingleSliderLightingConfig[K]): void {
    const arr = this.padSliders(i + 1);
    arr[i] = { ...arr[i], [key]: value };
    this.patch({ sliderConfigs: arr });
  }

  // ── per-control: slider labels ───────────────────────────────────────────────
  private padSliderLabels(len: number): SingleSliderLabelLightingConfig[] {
    const arr = [...(this.config()?.sliderLabelConfigs ?? [])];
    while (arr.length < len) arr.push({ ...SLIDER_LABEL_DEFAULT });
    return arr;
  }
  sliderLabelAt(i: number): SingleSliderLabelLightingConfig { return this.config()?.sliderLabelConfigs?.[i] ?? SLIDER_LABEL_DEFAULT; }
  setSliderLabel<K extends keyof SingleSliderLabelLightingConfig>(i: number, key: K, value: SingleSliderLabelLightingConfig[K]): void {
    const arr = this.padSliderLabels(i + 1);
    arr[i] = { ...arr[i], [key]: value };
    this.patch({ sliderLabelConfigs: arr });
  }

  // ── per-control: logo ─────────────────────────────────────────────────────────
  readonly logo = computed<SingleLogoLightingConfig>(() => this.config()?.logoConfig ?? LOGO_DEFAULT);
  setLogo<K extends keyof SingleLogoLightingConfig>(key: K, value: SingleLogoLightingConfig[K]): void {
    const cur = this.config()?.logoConfig ?? LOGO_DEFAULT;
    this.patch({ logoConfig: { ...cur, [key]: value } });
  }

  // ── icons for chip swatches ───────────────────────────────────────────────────
  gridIcon: IconName = 'grid';

  // ── persistence ───────────────────────────────────────────────────────────────
  private save(): void {
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => this.flush(), 350);
  }

  private flush(): void {
    const cfg = this.config();
    if (!cfg) return;
    this.deviceService.setLighting(this.serial(), cfg)
      .subscribe({ error: () => this.toast.show('Could not save lighting', { kind: 'error' }) });
  }

  back(): void { this.router.navigate(['/']); }
}

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v));
}
