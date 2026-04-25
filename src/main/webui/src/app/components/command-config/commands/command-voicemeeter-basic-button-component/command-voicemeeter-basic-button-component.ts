import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVoiceMeeterBasicButton } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-voicemeeter-basic-button-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatInput,
  ],
  templateUrl: './command-voicemeeter-basic-button-component.html',
  styleUrl: './command-voicemeeter-basic-button-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVoiceMeeterBasicButtonComponent extends CommandComponent<CommandVoiceMeeterBasicButton> {
  protected readonly controlTypes = ['Input', 'Output'] as const;
  protected readonly buttonTypes = [
    'mono', 'Mute', 'solo', 'M.C', 'EQ',
    'A1', 'A2', 'A3', 'A4', 'A5',
    'B1', 'B2', 'B3', 'SEL',
    'MIXA', 'MIXB', 'Repeat', 'Composite'
  ] as const;
}
