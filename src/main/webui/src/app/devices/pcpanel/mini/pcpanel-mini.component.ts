import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { LightingConfig } from '../../../models/generated/backend.types';
import { breathBaseColor, lightingAnimClass, lightingAnimDuration, lightingBreathMin, rainbowBaseColor, waveBaseColor } from '../lighting-animation';

function truncate(s: string, n: number): string {
  return s.length > n ? s.substring(0, n) + '…' : s;
}

const KNOB_RING_PATH =
  'M71.08,36.52v1.83H64.66A29.26,29.26,0,0,1,61.2,51.61l1.95,1.13-.92,1.58L60.29,53.2a28.94,28.94,0,0,1-9.4,9.41L54,68.07,52.46,69' +
  'l-4.24-7.33.8-.46h0a27,27,0,0,0,9.83-36.91l0-.05A27,27,0,1,0,21.93,61.11l.88.51L18.55,69,17,68.07l3.17-5.49a29,29,0,0,1-9.38-9.42' +
  'l-1.67,1-.92-1.59,1.67-1A29.2,29.2,0,0,1,6.4,38.35H0V36.52H6.42A29.2,29.2,0,0,1,9.86,24.08l-1.59-.92.91-1.58,1.6.92a29,29,0,0,1,' +
  '9.39-9.42L17,7.52l1.58-.91,3.21,5.55A28.66,28.66,0,0,1,34.62,8.72V7.16h1.83V8.72c.8,0,1.59.08,2.39.16a29,29,0,0,1,10.47,3.29l3.21,' +
  '-5.56,1.59.91L50.9,13.08a29.37,29.37,0,0,1,9.4,9.42l1.82-1.05L63,23l-1.82,1.05A28.8,28.8,0,0,1,64.55,34.9c0,.54.07,1.08.1,1.62Z';

@Component({
  selector: 'pcpanel-pcpanel-mini',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../device-visual.scss',
  templateUrl: './pcpanel-mini.component.html',
})
export class PcpanelMiniComponent {
  analogValues = input<number[]>([]);
  lightingConfig = input<any>(null);
  activeDial = input<number | null>(null);
  dialLabels = input<Map<number, string>>(new Map());
  dialClick = output<number>();

  readonly knobRingPath = KNOB_RING_PATH;
  readonly knobIndices = [0, 1, 2, 3];

  // Knob positions from PCPanelMiniUI.initButtons (in 600×270 viewBox)
  private readonly KNOB_POS: [number, number][] = [
    [56.3, 133.4], [171.3, 133.4], [286.3, 133.4], [401.3, 133.4],
  ];

  knobTransform(i: number): string {
    const [x, y] = this.KNOB_POS[i];
    return `translate(${x},${y})`;
  }

  knobRingTransform(i: number): string {
    // lightPanes at x=65, y=82, prefWidth=450, 4 columns → each col = 112.5
    const x = 65 + i * 112.5;
    return `translate(${x}, 82)`;
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

  knobRingColor(i: number): string {
    const cfg = this.lightingConfig() as LightingConfig | null;
    if (!cfg) return 'none';
    if (cfg.lightingMode === 'ALL_RAINBOW') return rainbowBaseColor(cfg, i, this.knobIndices.length);
    if (cfg.lightingMode === 'ALL_WAVE') return waveBaseColor(cfg, i, this.knobIndices.length);
    if (cfg.lightingMode === 'ALL_BREATH') return breathBaseColor(cfg);
    if (cfg.lightingMode === 'ALL_COLOR') return cfg.allColor ?? '#ffc940';
    if (cfg.lightingMode === 'SINGLE_COLOR') return cfg.individualColors?.[i] ?? '#ffc940';
    return '#ffc940';
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
