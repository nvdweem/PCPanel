import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router } from '@angular/router';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceService } from '../../services/device.service';
import {
  ColorPickerComponent, IconComponent, IconName, SegmentedComponent, SegmentOption, SelectComponent,
  SelectOption, SliderComponent, StatusDotComponent, ToastService, ToggleComponent,
} from '../../ui';
import { PcDeviceComponent } from '../../devices/visual/pc-device.component';
import { normalizeLogo } from '../../features/lighting/lighting-util';
import { DebugService } from '../../services/debug.service';
import {
  LightingConfig, LightingMode, SingleKnobLightingConfig, SingleLogoLightingConfig,
  SingleSliderLabelLightingConfig, SingleSliderLightingConfig, SINGLE_LOGO_MODE,
} from '../../models/generated/backend.types';

interface ModeChip { value: LightingMode; label: string; swatch: 'solid' | 'rainbow' | 'wave' | 'breath' | 'custom'; span2?: boolean; }

const KNOB_DEFAULT: SingleKnobLightingConfig = { mode: 'STATIC', color1: '#FFB020', color2: '#000000' };
const SLIDER_DEFAULT: SingleSliderLightingConfig = { mode: 'STATIC', color1: '#FFB020', color2: '#000000', muteOverrideColor: '#000000', muteOverrideDeviceOrFollow: '' };
const SLIDER_LABEL_DEFAULT: SingleSliderLabelLightingConfig = { mode: 'STATIC', color: '#FFB020', muteOverrideColor: '#000000', muteOverrideDeviceOrFollow: '' };
// brightness/speed/hue are signed bytes (-128..127, read unsigned). -1 = full brightness.
const LOGO_DEFAULT: SingleLogoLightingConfig = { mode: 'STATIC', color: '#FFB020', brightness: -1, hue: 0, speed: 32 };
const BLACK = '#000000';
const isBlackHex = (c: string | undefined): boolean => !c || /^#?0{3,8}$/i.test(c.trim());

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
  private readonly debug = inject(DebugService);
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

  // UI modes — "Off" maps to STATIC black (NONE leaves the LED's last color on the
  // device); "Follow slider" mirrors the slider's color into its label.
  readonly knobModeOptions: SelectOption[] = [
    { value: 'off', label: 'Off' },
    { value: 'static', label: 'Static color' },
    { value: 'gradient', label: 'Volume gradient' },
  ];
  readonly sliderModeOptions: SelectOption[] = [
    { value: 'off', label: 'Off' },
    { value: 'static', label: 'Static color' },
    { value: 'static-gradient', label: 'Static gradient' },
    { value: 'gradient', label: 'Volume gradient' },
  ];
  readonly sliderLabelModeOptions: SelectOption[] = [
    { value: 'off', label: 'Off' },
    { value: 'follow', label: 'Follow slider' },
    { value: 'static', label: 'Static color' },
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
      untracked(() => {
        const cfg = clone(s.lightingConfig);
        // Correct a non-Mini device left in the unsupported vertical-rainbow state
        // (its LEDs go white): force horizontal and push the fix once if it's live.
        if (cfg && !this.isMini() && cfg.rainbowVertical !== 0) {
          const wasLiveRainbow = cfg.lightingMode === 'ALL_RAINBOW';
          this.config.set({ ...cfg, rainbowVertical: 0 });
          if (wasLiveRainbow) this.flush();
          return;
        }
        this.config.set(cfg);
      });
    });
  }

  // ── device topology ────────────────────────────────────────────────────────
  readonly isPro = computed(() => (this.debug.deviceTypeOverride() || this.snapshot()?.deviceType) === 'PCPANEL_PRO');
  /** Vertical rainbow is a Mini-only hardware feature (Pro/RGB show white if mode 2 is sent). */
  readonly isMini = computed(() => (this.debug.deviceTypeOverride() || this.snapshot()?.deviceType) === 'PCPANEL_MINI');
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

  setMode(value: LightingMode): void {
    const cur = this.config();
    if (!cur || cur.lightingMode === value) { if (cur) this.patch({ lightingMode: value }); return; }
    const next = { ...cur, lightingMode: value };
    this.config.set(next);                       // update the editor UI immediately
    if (this.saveTimer) clearTimeout(this.saveTimer);
    // Clear every LED first so colours from the previous mode don't linger
    // (e.g. CUSTOM 'off' entries don't re-send a colour), then apply the new mode.
    const black: LightingConfig = { ...cur, lightingMode: 'ALL_COLOR', allColor: '#000000' };
    const applyNew = () => this.deviceService.setLighting(this.serial(), this.normalize(next))
      .subscribe({ error: () => this.toast.show('Could not save lighting', { kind: 'error' }) });
    this.deviceService.setLighting(this.serial(), black).subscribe({ next: applyNew, error: applyNew });
  }

  /**
   * Animation hue/speed/brightness/phase fields are signed bytes on the backend
   * (-128..127) read as unsigned 0..255. The sliders present the unsigned value;
   * `setByte` stores the signed representation Jackson will accept as a byte.
   */
  u8(v: number | undefined): number { return (((v ?? 0) % 256) + 256) % 256; }
  setByte<K extends keyof LightingConfig>(key: K, unsigned: number): void {
    const u = Math.max(0, Math.min(255, Math.round(unsigned)));
    this.set(key, (u > 127 ? u - 256 : u) as LightingConfig[K]);
  }

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
  knobUiMode(i: number): string {
    const c = this.knobAt(i);
    if (c.mode === 'NONE' || (c.mode === 'STATIC' && isBlackHex(c.color1))) return 'off';
    return c.mode === 'VOLUME_GRADIENT' ? 'gradient' : 'static';
  }
  setKnobUiMode(i: number, ui: string): void {
    const arr = this.padKnobs(i + 1); const cur = arr[i];
    if (ui === 'off') arr[i] = { ...cur, mode: 'STATIC', color1: BLACK, color2: BLACK };
    else if (ui === 'gradient') arr[i] = { ...cur, mode: 'VOLUME_GRADIENT', color1: isBlackHex(cur.color1) ? '#FFB020' : cur.color1, color2: isBlackHex(cur.color2) ? '#3B6BFF' : cur.color2 };
    else arr[i] = { ...cur, mode: 'STATIC', color1: isBlackHex(cur.color1) ? '#FFB020' : cur.color1 };
    this.patch({ knobConfigs: arr });
  }
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
  /** The colour a "follow" label should mirror: the slider's colour, or black when the slider is off. */
  private followColor(slider: SingleSliderLightingConfig | undefined): string {
    if (!slider || slider.mode === 'NONE' || (slider.mode === 'STATIC' && isBlackHex(slider.color1))) return BLACK;
    return slider.color1 || '#FFB020';
  }
  sliderUiMode(i: number): string {
    const c = this.sliderAt(i);
    if (c.mode === 'NONE' || (c.mode === 'STATIC' && isBlackHex(c.color1))) return 'off';
    return c.mode === 'STATIC_GRADIENT' ? 'static-gradient' : c.mode === 'VOLUME_GRADIENT' ? 'gradient' : 'static';
  }
  setSliderUiMode(i: number, ui: string): void {
    const arr = this.padSliders(i + 1); const cur = arr[i];
    const oldColor1 = cur.color1;
    const c1 = isBlackHex(cur.color1) ? '#FFB020' : cur.color1;
    const c2 = isBlackHex(cur.color2) ? '#3B6BFF' : cur.color2;
    if (ui === 'off') arr[i] = { ...cur, mode: 'STATIC', color1: BLACK, color2: BLACK };
    else if (ui === 'static-gradient') arr[i] = { ...cur, mode: 'STATIC_GRADIENT', color1: c1, color2: c2 };
    else if (ui === 'gradient') arr[i] = { ...cur, mode: 'VOLUME_GRADIENT', color1: c1, color2: c2 };
    else arr[i] = { ...cur, mode: 'STATIC', color1: c1 };
    this.patch(this.withFollowingLabel({ sliderConfigs: arr }, i, oldColor1, arr[i].color1));
  }
  setSlider<K extends keyof SingleSliderLightingConfig>(i: number, key: K, value: SingleSliderLightingConfig[K]): void {
    const sArr = this.padSliders(i + 1);
    const oldColor1 = sArr[i].color1;
    sArr[i] = { ...sArr[i], [key]: value };
    let part: Partial<LightingConfig> = { sliderConfigs: sArr };
    if (key === 'color1') part = this.withFollowingLabel(part, i, oldColor1, value as string);
    this.patch(part);
  }

  /**
   * A persisted label "follows" its slider when it's a STATIC colour equal to the slider's colour.
   * When the slider's primary colour changes (mode switch or colour edit), keep such a label in sync.
   * (Labels with no persisted config already follow live via sliderLabelAt().)
   */
  private withFollowingLabel(part: Partial<LightingConfig>, i: number, oldColor1: string, newColor1: string): Partial<LightingConfig> {
    const labels = this.config()?.sliderLabelConfigs;
    const lbl = labels?.[i];
    if (lbl && lbl.mode === 'STATIC' && (lbl.color ?? '') === (oldColor1 ?? '')) {
      const lArr = [...labels!];
      lArr[i] = { ...lbl, color: newColor1 };
      part.sliderLabelConfigs = lArr;
    }
    return part;
  }

  // ── per-control: slider labels (Off / Follow slider / Static) ─────────────────
  private padSliderLabels(len: number): SingleSliderLabelLightingConfig[] {
    const arr = [...(this.config()?.sliderLabelConfigs ?? [])];
    // New labels default to "follow" — STATIC mirroring their slider's colour (black if the slider is off).
    while (arr.length < len) arr.push({ ...SLIDER_LABEL_DEFAULT, mode: 'STATIC', color: this.followColor(this.sliderAt(arr.length)) });
    return arr;
  }
  sliderLabelAt(i: number): SingleSliderLabelLightingConfig {
    const c = this.config()?.sliderLabelConfigs?.[i];
    // Default (NONE / unset) follows the slider — the new UI only ever writes STATIC, so NONE = default.
    if (!c || c.mode === 'NONE') return { ...SLIDER_LABEL_DEFAULT, mode: 'STATIC', color: this.followColor(this.sliderAt(i)) };
    return c;
  }
  sliderLabelUiMode(i: number): string {
    const c = this.config()?.sliderLabelConfigs?.[i];
    if (!c || c.mode === 'NONE') return 'follow';   // default = follow the slider
    if (c.mode === 'STATIC' && isBlackHex(c.color)) return 'off';
    if (c.mode === 'STATIC' && c.color === this.sliderAt(i).color1) return 'follow';
    return 'static';
  }
  setSliderLabelUiMode(i: number, ui: string): void {
    const arr = this.padSliderLabels(i + 1); const cur = arr[i];
    if (ui === 'off') arr[i] = { ...cur, mode: 'STATIC', color: BLACK };
    else if (ui === 'follow') arr[i] = { ...cur, mode: 'STATIC', color: this.followColor(this.sliderAt(i)) };
    else arr[i] = { ...cur, mode: 'STATIC', color: isBlackHex(cur.color) ? '#FFB020' : cur.color };
    this.patch({ sliderLabelConfigs: arr });
  }
  setSliderLabel<K extends keyof SingleSliderLabelLightingConfig>(i: number, key: K, value: SingleSliderLabelLightingConfig[K]): void {
    const arr = this.padSliderLabels(i + 1);
    arr[i] = { ...arr[i], [key]: value };
    this.patch({ sliderLabelConfigs: arr });
  }

  // ── per-control: logo ─────────────────────────────────────────────────────────
  readonly logo = computed<SingleLogoLightingConfig>(() => this.config()?.logoConfig ?? LOGO_DEFAULT);
  setLogoMode(mode: SINGLE_LOGO_MODE): void {
    const cur = this.config()?.logoConfig ?? LOGO_DEFAULT;
    // Ensure brightness/speed are lit for animated modes (signed byte -1 = full).
    const next: SingleLogoLightingConfig = mode === 'NONE'
      ? { ...cur, mode }
      : { ...cur, mode, color: cur.color || '#FFB020', brightness: cur.brightness || -1, speed: cur.speed || 32 };
    this.patch({ logoConfig: next });
  }
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
    this.deviceService.setLighting(this.serial(), this.normalize(cfg))
      .subscribe({ error: () => this.toast.show('Could not save lighting', { kind: 'error' }) });
  }

  /** Apply all save-time normalizations: logo defaults, Mini-only vertical rainbow, follow-labels. */
  private normalize(cfg: LightingConfig): LightingConfig {
    let out = normalizeLogo(cfg);
    // Vertical rainbow is Mini-only; never emit mode 2 for Pro/RGB (it whites out the LEDs).
    if (!this.isMini()) out = { ...out, rainbowVertical: 0 };
    return this.withDefaultFollowingLabels(out);
  }

  /**
   * Slider labels default to "follow the slider": persist any unset/NONE label (Pro CUSTOM mode)
   * as STATIC mirroring its slider's colour, so the physical device follows too — not just the
   * editor. Explicit "off" is STATIC black and explicit colours are STATIC custom, so neither is
   * touched (the new UI never writes NONE).
   */
  private withDefaultFollowingLabels(cfg: LightingConfig): LightingConfig {
    if (!this.isPro() || cfg.lightingMode !== 'CUSTOM') return cfg;
    const sliders = cfg.sliderConfigs ?? [];
    const labels = [...(cfg.sliderLabelConfigs ?? [])];
    let changed = false;
    for (let i = 0; i < this.sliderIndexes.length; i++) {
      if (!labels[i] || labels[i].mode === 'NONE') {
        labels[i] = { ...SLIDER_LABEL_DEFAULT, mode: 'STATIC', color: this.followColor(sliders[i]) };
        changed = true;
      }
    }
    return changed ? { ...cfg, sliderLabelConfigs: labels } : cfg;
  }

  back(): void { this.router.navigate(['/']); }
}

function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v));
}
