import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommandBrightness } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-brightness-component',
  imports: [FormsModule],
  templateUrl: './command-brightness-component.html',
  styleUrl: './command-brightness-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandBrightnessComponent extends CommandComponent<CommandBrightness> {
}
