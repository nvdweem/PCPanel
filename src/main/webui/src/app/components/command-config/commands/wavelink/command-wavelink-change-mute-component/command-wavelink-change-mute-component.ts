import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandWaveLinkChangeMute } from '../../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { WaveLinkService } from '../../wavelink.service';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { CommonModule, TitleCasePipe } from '@angular/common';
import { CommandComponent } from '../../command.component';
import { WaveLinkIdLists } from '../wave-link-id-lists/wave-link-id-lists';

@Component({
  selector: 'app-command-wavelink-change-mute-component',
  imports: [
    CommonModule,
    FormsModule,
    FormField,
    MatRadioButton,
    MatRadioGroup,
    TitleCasePipe,
    WaveLinkIdLists,
  ],
  templateUrl: './command-wavelink-change-mute-component.html',
  styleUrl: './command-wavelink-change-mute-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandWaveLinkChangeMuteComponent extends CommandComponent<CommandWaveLinkChangeMute> {
  protected waveLinkService = inject(WaveLinkService);
  protected readonly commandTypes = ['Input', 'Channel', 'Mix', 'Output'] as const;
  protected readonly muteTypes = ['toggle', 'mute', 'unmute'] as const;
}
