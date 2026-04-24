import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommandComponent } from '../command.component';
import { CommandVolumeFocus } from '../../../../models/generated/backend.types';

@Component({
  selector: 'app-command-volume-focus-component',
  imports: [],
  templateUrl: './command-volume-focus-component.html',
  styleUrl: './command-volume-focus-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeFocusComponent extends CommandComponent<CommandVolumeFocus> {
}
