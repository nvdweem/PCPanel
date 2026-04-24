import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandVoiceMeeterBasic } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { VoiceMeeterService } from '../voicemeeter.service';
import { MatFormField, MatInput, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommonModule } from '@angular/common';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-voicemeeter-basic-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatInput,
  ],
  templateUrl: './command-voicemeeter-basic-component.html',
  styleUrl: './command-voicemeeter-basic-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandVoiceMeeterBasicComponent extends CommandComponent<CommandVoiceMeeterBasic> {
  protected voiceMeeterService = inject(VoiceMeeterService);
  protected readonly controlTypes = ['Input', 'Output'] as const;
  protected readonly allDialTypes = [
    'Gain', 'Audibility', 'Comp', 'Gate', 'Limit',
    'EQ Gain 1', 'EQ Gain 2', 'EQ Gain 3',
    'Reverb', 'Delay', 'FX 1', 'FX 2',
    'Return Reverb', 'Return Delay', 'Return FX 1', 'Return FX 2'
  ] as const;

  /** Returns available dial types for the currently selected channel index from the service, falling back to all types. */
  protected availableDialTypes = computed(() => {
    const params = this.voiceMeeterService.basicParams.value();
    const idx = this.field().index().value();
    if (params && params[idx]?.params?.length) {
      return params[idx].params;
    }
    return this.allDialTypes as unknown as string[];
  });
}
