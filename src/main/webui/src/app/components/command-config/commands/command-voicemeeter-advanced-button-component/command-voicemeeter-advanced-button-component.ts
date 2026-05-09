import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVoiceMeeterAdvancedButton } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { CommonModule } from '@angular/common';
import { VoiceMeeterService } from '../voicemeeter.service';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-voicemeeter-advanced-button-component',
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
  templateUrl: './command-voicemeeter-advanced-button-component.html',
  styleUrl: './command-voicemeeter-advanced-button-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVoiceMeeterAdvancedButtonComponent extends CommandComponent<CommandVoiceMeeterAdvancedButton> {
  protected voiceMeeterService = inject(VoiceMeeterService);
  protected readonly buttonControlModes = ['Enable', 'Disable', 'Toggle', 'String'] as const;

  protected allParams = computed(() => {
    const groups = this.voiceMeeterService.advancedParams.value() ?? [];
    return groups.flatMap(g => g.params.map(p => `${g.name}.${p}`));
  });
}
