import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandObsMuteSource } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { ObsService } from '../obs.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { TitleCasePipe } from '@angular/common';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-obs-mute-source-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatRadioButton,
    MatRadioGroup,
    TitleCasePipe,
  ],
  templateUrl: './command-obs-mute-source-component.html',
  styleUrl: './command-obs-mute-source-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandObsMuteSourceComponent extends CommandComponent<CommandObsMuteSource> {
  protected obsService = inject(ObsService);
  protected readonly muteTypes = ['toggle', 'mute', 'unmute'] as const;
}
