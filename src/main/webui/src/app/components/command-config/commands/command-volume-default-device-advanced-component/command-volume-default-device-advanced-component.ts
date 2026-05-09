import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVolumeDefaultDeviceAdvanced } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { AudioService } from '../audio.service';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption } from '@angular/material/select';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { CommandComponent } from '../command.component';
import { AudioDevice } from '../../../../models/models';
import { filtered } from '../../../../../shared';

const deviceFilter = (key: string) => {
  const lc = key.toLowerCase();
  return (device: AudioDevice) => device.name.toLowerCase().includes(lc);
};

@Component({
  selector: 'app-command-volume-default-device-advanced-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatInput,
    MatAutocomplete,
    MatAutocompleteTrigger,
  ],
  templateUrl: './command-volume-default-device-advanced-component.html',
  styleUrl: './command-volume-default-device-advanced-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeDefaultDeviceAdvancedComponent extends CommandComponent<CommandVolumeDefaultDeviceAdvanced> {
  protected devicesService = inject(AudioService);
  protected filteredMediaPb = filtered(computed(() => this.field().mediaPb().value()), this.devicesService.devices.value, deviceFilter);
  protected filteredMediaRec = filtered(computed(() => this.field().mediaRec().value()), this.devicesService.devices.value, deviceFilter);
  protected filteredCommPb = filtered(computed(() => this.field().communicationPb().value()), this.devicesService.devices.value, deviceFilter);
  protected filteredCommRec = filtered(computed(() => this.field().communicationRec().value()), this.devicesService.devices.value, deviceFilter);
}
