import { ChangeDetectionStrategy, Component, inject, linkedSignal } from '@angular/core';
import { MatRadioButton, MatRadioChange, MatRadioGroup } from '@angular/material/radio';
import { CommandVolumeDeviceMute } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { AudioService } from '../audio.service';
import { FormField } from '@angular/forms/signals';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-device-mute-component',
  imports: [
    FormsModule,
    MatRadioGroup,
    MatRadioButton,
    FormField,
    MatFormField,
    MatLabel,
    MatSelect,
    MatOption,
  ],
  templateUrl: './command-volume-device-mute-component.html',
  styleUrl: './command-volume-device-mute-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeDeviceMuteComponent extends CommandComponent<CommandVolumeDeviceMute> {
  protected devicesService = inject(AudioService);
  protected specificDevice = linkedSignal(() => !!this.field().deviceId().value());

  protected changeDefault(event: MatRadioChange) {
    if (!event.value) {
      this.field().deviceId().value.set('');
    }
  }
}
