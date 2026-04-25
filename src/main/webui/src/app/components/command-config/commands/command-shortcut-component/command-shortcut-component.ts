import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandShortcut } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-shortcut-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatInput,
  ],
  templateUrl: './command-shortcut-component.html',
  styleUrl: './command-shortcut-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandShortcutComponent extends CommandComponent<CommandShortcut> {
}
