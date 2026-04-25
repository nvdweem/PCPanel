import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { breathBaseColor, lightingAnimClass, lightingAnimDuration, lightingBreathMin, rainbowBaseColor, waveBaseColor, } from '../lighting-animation';
import { LightingConfig } from '../../../models/generated/backend.types';

function truncate(s: string, n: number): string {
  return s.length > n ? s.substring(0, n) + '…' : s;
}

@Component({
  selector: 'pcpanel-pcpanel-rgb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../device-visual.scss',
  templateUrl: './pcpanel-rgb.component.html',
})
export class PcpanelRgbComponent {
  analogValues = input<number[]>([]);
  lightingConfig = input<any>(null);
  activeDial = input<number | null>(null);
  dialLabels = input<Map<number, string>>(new Map());
  dialClick = output<number>();

  readonly knobIndices = [0, 1, 2, 3];

  // Knob positions from PCPanelRGBUI.initButtons (in 600×170 viewBox)
  private readonly KNOB_POS: [number, number][] = [
    [52, 64], [159.3, 64], [266.6, 64], [373.9, 64],
  ];

  knobTransform(i: number): string {
    const [x, y] = this.KNOB_POS[i];
    return `translate(${x},${y})`;
  }

  /** Rotation: 0-255 → 30°–330° */
  knobAngle(i: number): number {
    const v = this.analogValues()[i] ?? 0;
    return 3 * (v / 2.55) + 30;
  }

  readonly animClass = computed(() => {
    const cfg = this.lightingConfig() as LightingConfig | null;
    return cfg ? lightingAnimClass(cfg) : '';
  });

  readonly animDuration = computed(() => {
    const cfg = this.lightingConfig() as LightingConfig | null;
    return cfg ? lightingAnimDuration(cfg) : '0s';
  });

  readonly breathMin = computed(() => {
    const cfg = this.lightingConfig() as LightingConfig | null;
    return cfg ? lightingBreathMin(cfg) : 0.18;
  });

  ledColor(i: number): string {
    const cfg = this.lightingConfig() as LightingConfig | null;
    if (!cfg) return 'dodgerblue';
    if (cfg.lightingMode === 'ALL_RAINBOW') return rainbowBaseColor(cfg, i, this.knobIndices.length);
    if (cfg.lightingMode === 'ALL_WAVE') return waveBaseColor(cfg, i, this.knobIndices.length);
    if (cfg.lightingMode === 'ALL_BREATH') return breathBaseColor(cfg);
    if (cfg.lightingMode === 'ALL_COLOR') return cfg.allColor ?? 'dodgerblue';
    if (cfg.lightingMode === 'SINGLE_COLOR') return cfg.individualColors?.[i] ?? 'dodgerblue';
    return 'dodgerblue';
  }

  isActive(i: number): boolean {
    return this.activeDial() === i;
  }

  label(i: number): string {
    return truncate(this.dialLabels().get(i) ?? '—', 9);
  }

  onDialClick(i: number): void {
    this.dialClick.emit(i);
  }
}
