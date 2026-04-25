import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommandVolumeDefaultDeviceToggle } from '../../../../models/generated/backend.types';
import { AudioService } from '../audio.service';
import { CommonModule } from '@angular/common';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-default-device-toggle-component',
  imports: [
    CommonModule,
  ],
  templateUrl: './command-volume-default-device-toggle-component.html',
  styleUrl: './command-volume-default-device-toggle-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeDefaultDeviceToggleComponent extends CommandComponent<CommandVolumeDefaultDeviceToggle> {
  protected devicesService = inject(AudioService);

  protected isDeviceSelected(deviceId: string) {
    return this.field().devices().value().includes(deviceId);
  }

  protected toggleDevice(deviceId: string, checked: boolean) {
    this.field().devices().value.update(current =>
      checked
        ? current.includes(deviceId) ? current : [...current, deviceId]
        : current.filter(id => id !== deviceId)
    );
  }
}
