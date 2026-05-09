import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandRun } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-run-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatInput,
  ],
  templateUrl: './command-run-component.html',
  styleUrl: './command-run-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandRunComponent extends CommandComponent<CommandRun> {
}
