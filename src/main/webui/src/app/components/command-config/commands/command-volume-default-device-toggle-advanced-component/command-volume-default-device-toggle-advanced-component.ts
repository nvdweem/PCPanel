import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommandVolumeDefaultDeviceToggleAdvanced, DeviceSet } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { AudioService } from '../audio.service';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-default-device-toggle-advanced-component',
  imports: [
    CommonModule,
    FormsModule,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatButton,
    MatInput,
  ],
  templateUrl: './command-volume-default-device-toggle-advanced-component.html',
  styleUrl: './command-volume-default-device-toggle-advanced-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeDefaultDeviceToggleAdvancedComponent extends CommandComponent<CommandVolumeDefaultDeviceToggleAdvanced> {
  protected devicesService = inject(AudioService);

  protected addDeviceSet() {
    this.field().devices().value.update(current => [...current, {
      name: 'New Device Set',
      mediaPlayback: '',
      mediaRecord: '',
      communicationPlayback: '',
      communicationRecord: '',
    }]);
  }

  protected removeDeviceSet(index: number) {
    this.field().devices().value.update(current => current.filter((_, i) => i !== index));
  }

  protected updateDeviceSet<K extends keyof DeviceSet>(index: number, key: K, value: DeviceSet[K]) {
    this.field().devices().value.update(current =>
      current.map((deviceSet, i) => i === index ? {...deviceSet, [key]: value} : deviceSet)
    );
  }
}
