import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVolumeProcessMute } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { CommonModule } from '@angular/common';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatOption } from '@angular/material/select';
import { TitleCasePipe } from '@angular/common';
import { AudioService } from '../audio.service';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-process-mute-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatRadioButton,
    MatRadioGroup,
    MatFormField,
    MatLabel,
    MatInput,
    MatButton,
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatOption,
    TitleCasePipe,
  ],
  templateUrl: './command-volume-process-mute-component.html',
  styleUrl: './command-volume-process-mute-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeProcessMuteComponent extends CommandComponent<CommandVolumeProcessMute> {
  protected audioService = inject(AudioService);
  protected readonly muteTypes = ['toggle', 'mute', 'unmute'] as const;

  protected addProcess() {
    this.field().processName().value.update(current => [...current, '']);
  }

  protected removeProcess(index: number) {
    this.field().processName().value.update(current => current.filter((_, i) => i !== index));
  }

  protected updateProcess(index: number, process: string) {
    this.field().processName().value.update(current => current.map((value, i) => i === index ? process : value));
  }
}
