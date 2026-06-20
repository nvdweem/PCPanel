import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { DeviceCapabilitiesService } from '../../services/device-capabilities.service';
import { PcDeviceComponent } from './pc-device.component';
import { GenericDeviceComponent } from './generic-device.component';
import { ControlClick } from './control-click';

/**
 * Dispatcher used wherever a device is rendered (Home stage, lighting preview,
 * Advanced view). Renders {@link PcDeviceComponent} (the bespoke PCPanel layout)
 * when the device's descriptor is a PCPanel one, else the generic
 * {@link GenericDeviceComponent} grid. Forwards the {@link ControlClick} contract
 * so callers are renderer-agnostic.
 */
@Component({
  selector: 'pc-device-renderer',
  standalone: true,
  imports: [PcDeviceComponent, GenericDeviceComponent],
  template: `
    @if (isPcPanel()) {
      <pc-device [serial]="serial()" [showLabels]="showLabels()" [showChips]="showChips()" [flat]="flat()"
                 [knobSize]="knobSize()" [faderHeight]="faderHeight()"
                 [selectedKind]="selectedKind()" [selectedIndex]="selectedIndex()"
                 (controlClick)="controlClick.emit($event)"></pc-device>
    } @else {
      <pc-generic-device [serial]="serial()" [showLabels]="showLabels()" [flat]="flat()"
                         [knobSize]="knobSize()" [faderHeight]="faderHeight()"
                         (controlClick)="controlClick.emit($event)"></pc-generic-device>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceRendererComponent {
  private readonly capsService = inject(DeviceCapabilitiesService);

  readonly serial = input.required<string>();
  readonly showLabels = input<boolean>(true);
  readonly showChips = input<boolean>(false);
  readonly flat = input<boolean>(false);
  readonly knobSize = input<number>(56);
  readonly faderHeight = input<number>(128);
  readonly selectedKind = input<ControlClick['kind'] | null>(null);
  readonly selectedIndex = input<number>(-1);

  readonly controlClick = output<ControlClick>();

  private readonly caps = this.capsService.forSerial(this.serial);
  readonly isPcPanel = this.caps.isPcPanel;
}
