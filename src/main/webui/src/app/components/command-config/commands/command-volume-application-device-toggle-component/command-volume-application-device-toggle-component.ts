import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVolumeApplicationDeviceToggle, DeviceSet } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { AudioService } from '../audio.service';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-application-device-toggle-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatButton,
    MatRadioButton,
    MatRadioGroup,
    MatInput,
    MatAutocomplete,
    MatAutocompleteTrigger,
  ],
  templateUrl: './command-volume-application-device-toggle-component.html',
  styleUrl: './command-volume-application-device-toggle-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeApplicationDeviceToggleComponent extends CommandComponent<CommandVolumeApplicationDeviceToggle> {
  protected devicesService = inject(AudioService);

  protected addProcess() {
    this.field().processes().value.update(current => [...current, '']);
  }

  protected removeProcess(index: number) {
    this.field().processes().value.update(current => current.filter((_, i) => i !== index));
  }

  protected updateProcess(index: number, process: string) {
    this.field().processes().value.update(current => current.map((value, i) => i === index ? process : value));
  }

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
