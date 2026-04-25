import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { CommandWaveLinkAddFocusToChannel } from '../../../../models/generated/backend.types';
import { FormsModule } from '@angular/forms';
import { WaveLinkService } from '../wavelink.service';
import { MatFormField, MatLabel } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { CommandComponent } from '../command.component';

@Component({
  selector: 'app-command-wavelink-add-focus-to-channel-component',
  imports: [
    FormsModule,
    FormField,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './command-wavelink-add-focus-to-channel-component.html',
  styleUrl: './command-wavelink-add-focus-to-channel-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandWaveLinkAddFocusToChannelComponent extends CommandComponent<CommandWaveLinkAddFocusToChannel> {
  protected waveLinkService = inject(WaveLinkService);
}
