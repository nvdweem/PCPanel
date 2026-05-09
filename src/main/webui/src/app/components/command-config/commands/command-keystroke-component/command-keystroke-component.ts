import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandKeystroke } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-keystroke-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatInput,
  ],
  templateUrl: './command-keystroke-component.html',
  styleUrl: './command-keystroke-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandKeystrokeComponent extends CommandComponent<CommandKeystroke> {
}
