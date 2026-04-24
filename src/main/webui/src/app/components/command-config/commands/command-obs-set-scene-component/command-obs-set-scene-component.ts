import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandObsSetScene } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { ObsService } from '../obs.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-obs-set-scene-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './command-obs-set-scene-component.html',
  styleUrl: './command-obs-set-scene-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandObsSetSceneComponent extends CommandComponent<CommandObsSetScene> {
  protected obsService = inject(ObsService);
}
