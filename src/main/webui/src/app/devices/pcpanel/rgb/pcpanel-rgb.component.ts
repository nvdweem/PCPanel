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

  // Knob positions from PCPanelRGBUI.initButtons (in 600×170 viewBox)
  private readonly KNOB_POS: [number, number][] = [
    [52, 64], [159.3, 64], [266.6, 64], [373.9, 64],
  ];

  protected knobTransform(i: number): string {
    const [x, y] = this.KNOB_POS[i];
    return `translate(${x},${y})`;
  }
}
