import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVoiceMeeterAdvanced } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { VoiceMeeterService } from '../voicemeeter.service';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { CommonModule } from '@angular/common';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-voicemeeter-advanced-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatInput,
    MatAutocomplete,
    MatAutocompleteTrigger,
  ],
  templateUrl: './command-voicemeeter-advanced-component.html',
  styleUrl: './command-voicemeeter-advanced-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVoiceMeeterAdvancedComponent extends CommandComponent<CommandVoiceMeeterAdvanced> {
  protected voiceMeeterService = inject(VoiceMeeterService);
  protected readonly controlModes = [
    '-12 to 12', '0 to 12', '-40 to 12', '-60 to 12', '-Inf to 12', '-Inf to 0'
  ] as const;

  /** Flat list of all advanced params from the service, formatted as "channelName.param". */
  protected allParams = computed(() => {
    const groups = this.voiceMeeterService.advancedParams.value() ?? [];
    return groups.flatMap(g => g.params.map(p => `${g.name}.${p}`));
  });
}
