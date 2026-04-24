import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandObsSetSourceVolume } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { ObsService } from '../obs.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-obs-set-source-volume-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './command-obs-set-source-volume-component.html',
  styleUrl: './command-obs-set-source-volume-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandObsSetSourceVolumeComponent extends CommandComponent<CommandObsSetSourceVolume> {
  protected obsService = inject(ObsService);
}
