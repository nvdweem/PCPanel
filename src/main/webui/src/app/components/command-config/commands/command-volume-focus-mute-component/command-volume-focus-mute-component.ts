import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVolumeFocusMute } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { TitleCasePipe } from '@angular/common';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-focus-mute-component',
  imports: [
    FormsModule,
    FormField,
    MatRadioButton,
    MatRadioGroup,
    TitleCasePipe,
  ],
  templateUrl: './command-volume-focus-mute-component.html',
  styleUrl: './command-volume-focus-mute-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeFocusMuteComponent extends CommandComponent<CommandVolumeFocusMute> {
  protected readonly muteTypes = ['toggle', 'mute', 'unmute'] as const;
}
