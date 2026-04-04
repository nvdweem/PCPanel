import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';

function mapRange(v: number, inMin: number, inMax: number, outMin: number, outMax: number): number {
  return ((v - inMin) / (inMax - inMin)) * (outMax - outMin) + outMin;
}
function truncate(s: string, n: number): string { return s.length > n ? s.substring(0, n) + '…' : s; }

const KNOB_RING_PATH =
  'M71.08,36.52v1.83H64.66A29.26,29.26,0,0,1,61.2,51.61l1.95,1.13-.92,1.58L60.29,53.2a28.94,28.94,0,0,1-9.4,9.41L54,68.07,52.46,69' +
  'l-4.24-7.33.8-.46h0a27,27,0,0,0,9.83-36.91l0-.05A27,27,0,1,0,21.93,61.11l.88.51L18.55,69,17,68.07l3.17-5.49a29,29,0,0,1-9.38-9.42' +
  'l-1.67,1-.92-1.59,1.67-1A29.2,29.2,0,0,1,6.4,38.35H0V36.52H6.42A29.2,29.2,0,0,1,9.86,24.08l-1.59-.92.91-1.58,1.6.92a29,29,0,0,1,' +
  '9.39-9.42L17,7.52l1.58-.91,3.21,5.55A28.66,28.66,0,0,1,34.62,8.72V7.16h1.83V8.72c.8,0,1.59.08,2.39.16a29,29,0,0,1,10.47,3.29l3.21,' +
  '-5.56,1.59.91L50.9,13.08a29.37,29.37,0,0,1,9.4,9.42l1.82-1.05L63,23l-1.82,1.05A28.8,28.8,0,0,1,64.55,34.9c0,.54.07,1.08.1,1.62Z';

const SLIDER_LABEL_PATH =
  'M8.68,1.25v2h-2v-1a.39.39,0,0,0-.38-.37H2.38A.39.39,0,0,0,2,2.3v3a.39.39,0,0,0,.38.37H7.32A1.32,1.32,0,' +
  '0,1,8.68,6.89V12a1.31,1.31,0,0,1-1.36,1.24h-6A1.32,1.32,0,0,1,0,12v-2H2v1a.38.38,0,0,0,.38.37H7.3A.38.38,' +
  '0,0,0,7.7,11V7.24a.37.37,0,0,0-.38-.37H2.38A1.32,1.32,0,0,1,1,5.61V1.25A1.31,1.31,0,0,1,2.38,0h4.94A1.31,1.31,0,0,1,8.68,1.25Z';

@Component({
  selector: 'app-pcpanel-pro',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './device-visual.scss',
  templateUrl: './pcpanel-pro.component.html',
})
export class PcpanelProComponent {
  analogValues = input<Map<number, number>>(new Map());
  lightingConfig = input<any>(null);
  activeDial = input<number | null>(null);
  dialLabels = input<Map<number, string>>(new Map());
  dialClick = output<number>();

  readonly knobRingPath = KNOB_RING_PATH;
  readonly sliderLabelPath = SLIDER_LABEL_PATH;
  readonly knobIndices = [0, 1, 2, 3, 4];
  readonly sliderIndices = [5, 6, 7, 8];
  readonly sliderLightSegs = [0, 1, 2, 3, 4];

  // Knob positions (top-left of knob button, 80×80 clickable area) in 480×610 viewBox
  private readonly KNOB_POS: [number, number][] = [
    [121.3, 66.3], [254.3, 66.3],
    [54.8, 163.8], [187.8, 163.8], [320.8, 163.8],
  ];
  // SliderHolder origins in 480×610 viewBox (30×225 each)
  private readonly SLIDER_POS: [number, number][] = [
    [76, 325], [167, 325], [260, 325], [350, 325],
  ];
  // Slider light pane origins (x only; y=328)
  private readonly SLIDER_LIGHT_X = [27, 118, 285, 376.5];

  // ── Transforms ──────────────────────────────────────────────────────────────

  knobTransform(i: number): string {
    const [x, y] = this.KNOB_POS[i];
    return `translate(${x},${y})`;
  }

  knobRingTransform(i: number): string {
    // lightPanes1 (knobs 0,1): x=126, y=31, colW=132.5
    // lightPanes  (knobs 2-4): x=60.5, y=128, colW=132.5
    let x: number, y: number;
    if (i < 2) { x = 126 + i * 132.5; y = 31; }
    else        { x = 60.5 + (i - 2) * 132.5; y = 128; }
    return `translate(${x},${y})`;
  }

  sliderTransform(si: number): string {
    const [x, y] = this.SLIDER_POS[si];
    return `translate(${x},${y})`;
  }

  sliderLightTransform(si: number): string {
    return `translate(${this.SLIDER_LIGHT_X[si]},328)`;
  }

  // ── Values ───────────────────────────────────────────────────────────────────

  /** Knob indicator rotation: 0-255 → 30°–330° */
  knobAngle(i: number): number {
    const v = this.analogValues().get(i) ?? 0;
    return 3 * (v / 2.55) + 30;
  }

  /** Slider thumb Y within 225px holder (val=0→bottom, val=255→top) */
  thumbY(si: number): number {
    const v = this.analogValues().get(si) ?? 0;
    return mapRange(v, 0, 255, 225 - 20, 0);
  }

  /** How many slider LED segments should be lit (0-5) based on value */
  litSegs(si: number): number {
    const v = this.analogValues().get(si) ?? 0;
    return Math.round(mapRange(v, 0, 255, 0, 5));
  }

  // ── Colors ────────────────────────────────────────────────────────────────────

  knobRingColor(i: number): string {
    const cfg = this.lightingConfig();
    if (!cfg) return 'none';
    if (cfg.lightingMode === 'ALL_COLOR') return cfg.allColor ?? '#ffc940';
    if (cfg.lightingMode === 'SINGLE_COLOR') return cfg.individualColors?.[i] ?? '#ffc940';
    return '#ffc940';
  }

  sliderSegColor(si: number, seg: number): string {
    const cfg = this.lightingConfig();
    const base = cfg?.lightingMode === 'ALL_COLOR' ? (cfg.allColor ?? '#4488ff')
               : cfg?.lightingMode === 'SINGLE_COLOR' ? (cfg.individualColors?.[si] ?? '#4488ff')
               : '#4488ff';
    return seg < this.litSegs(si) ? base : '#222';
  }

  isActive(i: number): boolean { return this.activeDial() === i; }

  label(i: number): string { return truncate(this.dialLabels().get(i) ?? '—', 9); }

  onDialClick(i: number): void { this.dialClick.emit(i); }
}
