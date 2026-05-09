import { Component, computed, inject, input } from '@angular/core';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { CommandWaveLinkChange } from '../../../../../models/generated/backend.types';
import { FieldTree, FormField } from '@angular/forms/signals';
import { WaveLinkService } from '../../wavelink.service';

interface WithIdAndName {
  id: string;
  name?: string;
}

interface FormState {
  id1Name?: string;
  id1List?: () => WithIdAndName[];
  id2Name?: string;
  id2List?: () => WithIdAndName[];
}

@Component({
  selector: 'app-wave-link-id-lists',
  imports: [
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    FormField
  ],
  templateUrl: './wave-link-id-lists.html',
  styleUrl: './wave-link-id-lists.scss',
})
export class WaveLinkIdLists {
  field = input.required<FieldTree<Required<CommandWaveLinkChange>>>();

  protected waveLinkService = inject(WaveLinkService);
  protected readonly commandTypes = ['Input', 'Channel', 'Mix', 'Output'] as const;

  protected formState = computed<FormState>(() => {
    const ct = this.field().commandType().value();
    switch (ct) {
      case 'Channel':
        return {
          id1Name: 'Channel',
          id1List: this.waveLinkService.channels,
        };
      case 'Input':
        return {
          id1Name: 'Input device',
          id1List: this.waveLinkService.inputs,
        };
      case 'Mix':
        return {
          id1Name: 'Channel',
          id1List: this.waveLinkService.channels,
          id2Name: 'Mix',
          id2List: this.waveLinkService.mixes,
        };
      case 'Output':
        return {
          id1Name: 'Output device',
          id1List: this.waveLinkService.outputs,
        };
    }
  });
}
