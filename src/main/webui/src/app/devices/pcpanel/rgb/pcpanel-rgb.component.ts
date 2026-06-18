import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PcpanelKnobDeviceBase } from '../pcpanel-knob-device.base';
import { LightingVariant } from '../command-config/command-config.component';

@Component({
  selector: 'pcpanel-pcpanel-rgb',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../device-visual.scss',
  templateUrl: './pcpanel-rgb.component.html',
})
export class PcpanelRgbComponent extends PcpanelKnobDeviceBase {
  protected readonly defaultColor = 'dodgerblue';
  protected readonly lightingVariant: LightingVariant = 'rgb-single';

  // Knob cut-out centres in the 600×170 viewBox. The body art has four circular
  // cut-outs (r≈46) produced by matrix(1.725,0,0,1.76,5.5,5.4) applied to local
  // centres (49.3,55.9),(112.7,…),(176,…),(239.4,…). The knob, its LED ring and
  // the top scale arc are all centred on these points so the dial sits in the
  // middle of its colored ring and the rings line up with the body cut-outs.
  protected readonly CUTOUT: readonly [number, number][] = [
    [90.5, 103.8], [199.9, 103.8], [309.1, 103.8], [418.5, 103.8],
  ];
  /** LED square behind each cut-out; ≥ cut-out diameter so the ring is never clipped. */
  protected readonly LED_SIZE = 104;

  protected knobTransform(i: number): string {
    const [cx, cy] = this.CUTOUT[i];
    // The knob body's local centre is (30,30); centre it on the cut-out.
    return `translate(${cx - 30},${cy - 30})`;
  }

  protected ledX(i: number): number {
    return this.CUTOUT[i][0] - this.LED_SIZE / 2;
  }

  protected ledY(i: number): number {
    return this.CUTOUT[i][1] - this.LED_SIZE / 2;
  }
}
