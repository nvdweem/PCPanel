import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommandWaveLinkChangeLevel } from '../../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { CommandComponent } from '../../command.component';
import { WaveLinkIdLists } from '../wave-link-id-lists/wave-link-id-lists';

@Component({
  selector: 'app-command-wavelink-change-level-component',
  imports: [
    CommonModule,
    FormsModule,
    WaveLinkIdLists,
  ],
  templateUrl: './command-wavelink-change-level-component.html',
  styleUrl: './command-wavelink-change-level-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandWaveLinkChangeLevelComponent extends CommandComponent<CommandWaveLinkChangeLevel> {
}
