import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';

function truncate(s: string, n: number): string { return s.length > n ? s.substring(0, n) + '…' : s; }

@Component({
  selector: 'app-pcpanel-rgb',
  standalone: true,
  imports: [NgFor, NgIf],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './device-visual.scss',
  templateUrl: './pcpanel-rgb.component.html',
})
export class PcpanelRgbComponent {
  @Input() analogValues: Map<number, number> = new Map();
  @Input() lightingConfig: any = null;
  @Input() activeDial: number | null = null;
  @Input() dialLabels: Map<number, string> = new Map();
  @Output() dialClick = new EventEmitter<number>();

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
    const v = this.analogValues.get(i) ?? 0;
    return 3 * (v / 2.55) + 30;
  }

  ledColor(i: number): string {
    const cfg = this.lightingConfig;
    if (!cfg) return 'dodgerblue';
    if (cfg.lightingMode === 'ALL_COLOR') return cfg.allColor ?? 'dodgerblue';
    if (cfg.lightingMode === 'SINGLE_COLOR') return cfg.individualColors?.[i] ?? 'dodgerblue';
    return 'dodgerblue';
  }

  isActive(i: number): boolean { return this.activeDial === i; }
  label(i: number): string { return truncate(this.dialLabels.get(i) ?? '—', 9); }
  onDialClick(i: number): void { this.dialClick.emit(i); }
}
