import { ChangeDetectionStrategy, Component, inject, linkedSignal } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVolumeDevice } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { AudioService } from '../audio.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatRadioButton, MatRadioChange, MatRadioGroup } from '@angular/material/radio';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-device-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatCheckbox,
    MatRadioGroup,
    MatRadioButton,
  ],
  templateUrl: './command-volume-device-component.html',
  styleUrl: './command-volume-device-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeDeviceComponent extends CommandComponent<CommandVolumeDevice> {
  protected devicesService = inject(AudioService);
  protected specificDevice = linkedSignal(() => !!this.field().deviceId().value());

  protected changeDefault(event: MatRadioChange) {
    if (!event.value) {
      this.field().deviceId().value.set('');
    }
  }
}
