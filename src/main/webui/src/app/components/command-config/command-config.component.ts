import {
  Component, EventEmitter, Input, OnChanges, Output, SimpleChanges
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  COMMAND_TYPE_DEFS, Command, CommandKind, CommandTypeDef, CommandsWrapper,
  DEFAULT_DIAL_PARAMS, DialCommandParams, MuteType
} from '../../models/models';
import { AudioPickerComponent } from '../audio-picker/audio-picker.component';
import { DialParamsEditorComponent } from './dial-params-editor.component';
const PKG = 'com.getpcpanel.commands.command.';

function makeDefault(type: string, kind: CommandKind): Command {
  switch (type) {
    case `${PKG}CommandNoOp`:
      return { _type: `${PKG}CommandNoOp` } as any;
    case `${PKG}CommandMedia`:
      return { _type: `${PKG}CommandMedia`, button: 'playPause', spotify: false } as any;
    case `${PKG}CommandKeystroke`:
      return { _type: `${PKG}CommandKeystroke`, keystroke: '' } as any;
    case `${PKG}CommandRun`:
      return { _type: `${PKG}CommandRun`, command: '' } as any;
    case `${PKG}CommandShortcut`:
      return { _type: `${PKG}CommandShortcut`, shortcut: '' } as any;
    case `${PKG}CommandEndProgram`:
      return { _type: `${PKG}CommandEndProgram`, specific: false, name: '' } as any;
    case `${PKG}CommandProfile`:
      return { _type: `${PKG}CommandProfile`, profile: '' } as any;
    case `${PKG}CommandBrightness`:
      return { _type: `${PKG}CommandBrightness`, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeFocus`:
      return { _type: `${PKG}CommandVolumeFocus`, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeFocusMute`:
      return { _type: `${PKG}CommandVolumeFocusMute`, muteType: 'toggle' as MuteType } as any;
    case `${PKG}CommandVolumeDevice`:
      return { _type: `${PKG}CommandVolumeDevice`, deviceId: '', isUnMuteOnVolumeChange: false, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeDeviceMute`:
      return { _type: `${PKG}CommandVolumeDeviceMute`, deviceId: '', muteType: 'toggle' as MuteType } as any;
    case `${PKG}CommandVolumeDefaultDevice`:
      return { _type: `${PKG}CommandVolumeDefaultDevice`, deviceId: '' } as any;
    case `${PKG}CommandVolumeDefaultDeviceToggle`:
      return { _type: `${PKG}CommandVolumeDefaultDeviceToggle`, devices: [] } as any;
    case `${PKG}CommandVolumeDefaultDeviceAdvanced`:
      return { _type: `${PKG}CommandVolumeDefaultDeviceAdvanced`, name: '', mediaPb: '', mediaRec: '', communicationPb: '', communicationRec: '' } as any;
    case `${PKG}CommandVolumeProcess`:
      return { _type: `${PKG}CommandVolumeProcess`, processName: [], device: '', isUnMuteOnVolumeChange: false, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeProcessMute`:
      return { _type: `${PKG}CommandVolumeProcessMute`, processName: [], muteType: 'toggle' as MuteType } as any;
    case `${PKG}CommandVolumeApplicationDeviceToggle`:
      return { _type: `${PKG}CommandVolumeApplicationDeviceToggle`, processes: [], followFocus: false, devices: [] } as any;
    case `${PKG}CommandObsMuteSource`:
      return { _type: `${PKG}CommandObsMuteSource`, source: '', type: 'toggle' as MuteType } as any;
    case `${PKG}CommandObsSetScene`:
      return { _type: `${PKG}CommandObsSetScene`, scene: '' } as any;
    case `${PKG}CommandObsSetSourceVolume`:
      return { _type: `${PKG}CommandObsSetSourceVolume`, sourceName: '', dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVoiceMeeterBasic`:
      return { _type: `${PKG}CommandVoiceMeeterBasic`, ct: 'STRIP', index: 0, dt: 'VOLUME', dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVoiceMeeterBasicButton`:
      return { _type: `${PKG}CommandVoiceMeeterBasicButton`, ct: 'STRIP', index: 0, bt: 'MONO' } as any;
    case `${PKG}CommandVoiceMeeterAdvanced`:
      return { _type: `${PKG}CommandVoiceMeeterAdvanced`, fullParam: '', ct: 'ABSOLUTE', dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVoiceMeeterAdvancedButton`:
      return { _type: `${PKG}CommandVoiceMeeterAdvancedButton`, fullParam: '', bt: 'SET', stringValue: null } as any;
    default:
      return { _type: `${PKG}CommandNoOp` } as any;
  }
}


@Component({
  selector: 'app-command-config',
  standalone: true,
  imports: [FormsModule, AudioPickerComponent, DialParamsEditorComponent],
  templateUrl: './command-config.component.html',
  styleUrl: './command-config.component.scss'
})
export class CommandConfigComponent implements OnChanges {
  /** 'dial' or 'button' */
  @Input() kind: CommandKind = 'button';
  @Input() index = 0;
  /** The current Commands wrapper from the API (may be null/undefined for new) */
  @Input() currentCommands: CommandsWrapper | null = null;
  /** Profiles available on this device (for CommandProfile) */
  @Input() profiles: string[] = [];

  @Output() saved = new EventEmitter<CommandsWrapper>();
  @Output() cancelled = new EventEmitter<void>();

  availableTypes: CommandTypeDef[] = [];
  selectedType = '';
  cmd: Record<string, any> = {};

  muteTypes: MuteType[] = ['mute', 'unmute', 'toggle'];
  vmControlTypes = ['STRIP', 'BUS'];
  vmDialTypes = ['VOLUME', 'GAIN', 'COMP', 'GATE', 'EQ'];
  vmButtonTypes = ['MONO', 'MUTE', 'SOLO'];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['kind'] || changes['currentCommands']) {
      this.availableTypes = COMMAND_TYPE_DEFS.filter(t => t.kinds.includes(this.kind));
      const existing = this.currentCommands?.commands?.[0];
      if (existing) {
        this.selectedType = existing._type;
        this.cmd = { ...existing } as any;
      } else {
        this.selectedType = this.availableTypes[0]?._type ?? '';
        this.cmd = makeDefault(this.selectedType, this.kind) as any;
      }
    }
  }

  onTypeChange(type: string): void {
    this.cmd = makeDefault(type, this.kind) as any;
  }

  is(shortName: string): boolean {
    return this.cmd['_type'] === `${PKG}${shortName}`;
  }

  /** Cast to mutable array (needed for Set<String> fields) */
  asArray(val: any): string[] {
    if (Array.isArray(val)) return val;
    return val ? [...val] : [];
  }

  removeItem(arr: any[], index: number): void {
    arr.splice(index, 1);
  }

  save(): void {
    const command = this.buildCommand();
    const wrapper: CommandsWrapper = {
      commands: command ? [command] : [],
      type: 'allAtOnce'
    };
    this.saved.emit(wrapper);
  }

  cancel(): void {
    this.cancelled.emit();
  }

  onOverlayClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-overlay')) {
      this.cancel();
    }
  }

  private buildCommand(): Command | null {
    if (this.cmd['_type'] === `${PKG}CommandNoOp`) return null;
    // For CommandVolumeProcessMute, processName is stored as array but Java expects Set
    return this.cmd as Command;
  }
}
