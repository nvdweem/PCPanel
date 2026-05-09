import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandEndProgram } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatRadioButton, MatRadioChange, MatRadioGroup } from '@angular/material/radio';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatOption } from '@angular/material/select';
import { AudioService } from '../audio.service';
import { CommandComponent } from '../command.component';
import { filtered, uniqueSorted } from '../../../../../shared';

@Component({
  selector: 'app-command-end-program-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatRadioButton,
    MatRadioGroup,
    MatInput,
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatOption,
  ],
  templateUrl: './command-end-program-component.html',
  styleUrl: './command-end-program-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandEndProgramComponent extends CommandComponent<CommandEndProgram> {
  protected audioService = inject(AudioService);
  protected specificProgram = linkedSignal(() => this.field().specific().value());
  protected filteredPrograms = uniqueSorted(filtered(computed(() => this.field().name().value()), this.audioService.processes.value, key => proc => proc.name.toLowerCase().includes(key.toLowerCase())), i => i.name);

  protected changeDefault(event: MatRadioChange) {
    if (!event.value) {
      this.field().name().value.set('');
    }
  }
}
