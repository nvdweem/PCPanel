import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandWaveLinkMainOutput } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { WaveLinkService } from '../wavelink.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-wavelink-main-output-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './command-wavelink-main-output-component.html',
  styleUrl: './command-wavelink-main-output-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandWaveLinkMainOutputComponent extends CommandComponent<CommandWaveLinkMainOutput> {
  protected waveLinkService = inject(WaveLinkService);
}
