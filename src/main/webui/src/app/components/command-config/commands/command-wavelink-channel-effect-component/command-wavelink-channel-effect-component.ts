import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandWaveLinkChannelEffect } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { WaveLinkService } from '../wavelink.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { CommonModule, TitleCasePipe } from '@angular/common';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-wavelink-channel-effect-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
    MatRadioButton,
    MatRadioGroup,
    TitleCasePipe,
  ],
  templateUrl: './command-wavelink-channel-effect-component.html',
  styleUrl: './command-wavelink-channel-effect-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandWaveLinkChannelEffectComponent extends CommandComponent<CommandWaveLinkChannelEffect> {
  protected waveLinkService = inject(WaveLinkService);
  protected readonly toggleTypes = ['toggle', 'mute', 'unmute'] as const;
}
