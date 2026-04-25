import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVolumeProcess } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { AudioService } from '../audio.service';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatCheckbox } from '@angular/material/checkbox';
import { CommonModule } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-volume-process-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatCheckbox,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatButton,
    MatInput,
    MatAutocomplete,
    MatAutocompleteTrigger,
  ],
  templateUrl: './command-volume-process-component.html',
  styleUrl: './command-volume-process-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVolumeProcessComponent extends CommandComponent<CommandVolumeProcess> {
  protected devicesService = inject(AudioService);

  protected addProcess() {
    this.field().processName().value.update(current => [...current, '']);
  }

  protected removeProcess(index: number) {
    this.field().processName().value.update(current => current.filter((_, i) => i !== index));
  }

  protected updateProcess(index: number, process: string) {
    this.field().processName().value.update(current => current.map((value, i) => i === index ? process : value));
  }
}
