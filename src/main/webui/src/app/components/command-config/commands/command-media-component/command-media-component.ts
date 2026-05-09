import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandMedia } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-media-component',
  imports: [
    FormsModule,
    FormField,
    MatCheckbox,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './command-media-component.html',
  styleUrl: './command-media-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandMediaComponent extends CommandComponent<CommandMedia> {
  protected readonly buttons = ['mute', 'next', 'prev', 'stop', 'playPause'] as const;
  protected readonly buttonLabels: Record<string, string> = {
    mute: 'Mute',
    next: 'Next',
    prev: 'Previous',
    stop: 'Stop',
    playPause: 'Play / Pause',
  };
}
