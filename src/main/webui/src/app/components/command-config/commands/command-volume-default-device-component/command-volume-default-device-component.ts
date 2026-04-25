import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVolumeDefaultDevice } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { AudioService } from '../audio.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-default-device-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './command-volume-default-device-component.html',
  styleUrl: './command-volume-default-device-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeDefaultDeviceComponent extends CommandComponent<CommandVolumeDefaultDevice> {
  protected devicesService = inject(AudioService);
}
