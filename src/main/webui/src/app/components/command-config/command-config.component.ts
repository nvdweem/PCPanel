import { Component, Inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { COMMAND_TYPE_DEFS, Command, CommandKind, CommandTypeDef, CommandsWrapper, DEFAULT_DIAL_PARAMS, MuteType } from '../../models/models';
import { AudioPickerComponent } from '../audio-picker/audio-picker.component';
import { DialParamsEditorComponent } from './dial-params-editor.component';

export interface CommandDialogData {
  kind: CommandKind;
  index: number;
  currentCommands: CommandsWrapper | null;
  profiles: string[];
}

const PKG = 'com.getpcpanel.commands.command.';

function makeDefault(type: string, kind: CommandKind): Command {
  switch (type) {
    case `${PKG}CommandNoOp`: return { _type: `${PKG}CommandNoOp` } as any;
    case `${PKG}CommandMedia`: return { _type: `${PKG}CommandMedia`, button: 'playPause', spotify: false } as any;
    case `${PKG}CommandKeystroke`: return { _type: `${PKG}CommandKeystroke`, keystroke: '' } as any;
    case `${PKG}CommandRun`: return { _type: `${PKG}CommandRun`, command: '' } as any;
    case `${PKG}CommandShortcut`: return { _type: `${PKG}CommandShortcut`, shortcut: '' } as any;
    case `${PKG}CommandEndProgram`: return { _type: `${PKG}CommandEndProgram`, specific: false, name: '' } as any;
    case `${PKG}CommandProfile`: return { _type: `${PKG}CommandProfile`, profile: '' } as any;
    case `${PKG}CommandBrightness`: return { _type: `${PKG}CommandBrightness`, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeFocus`: return { _type: `${PKG}CommandVolumeFocus`, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeFocusMute`: return { _type: `${PKG}CommandVolumeFocusMute`, muteType: 'toggle' as MuteType } as any;
    case `${PKG}CommandVolumeDevice`: return { _type: `${PKG}CommandVolumeDevice`, deviceId: '', isUnMuteOnVolumeChange: false, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeDeviceMute`: return { _type: `${PKG}CommandVolumeDeviceMute`, deviceId: '', muteType: 'toggle' as MuteType } as any;
    case `${PKG}CommandVolumeDefaultDevice`: return { _type: `${PKG}CommandVolumeDefaultDevice`, deviceId: '' } as any;
    case `${PKG}CommandVolumeDefaultDeviceToggle`: return { _type: `${PKG}CommandVolumeDefaultDeviceToggle`, devices: [] } as any;
    case `${PKG}CommandVolumeDefaultDeviceAdvanced`: return { _type: `${PKG}CommandVolumeDefaultDeviceAdvanced`, name: '', mediaPb: '', mediaRec: '', communicationPb: '', communicationRec: '' } as any;
    case `${PKG}CommandVolumeProcess`: return { _type: `${PKG}CommandVolumeProcess`, processName: [], device: '', isUnMuteOnVolumeChange: false, dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVolumeProcessMute`: return { _type: `${PKG}CommandVolumeProcessMute`, processName: [], muteType: 'toggle' as MuteType } as any;
    case `${PKG}CommandVolumeApplicationDeviceToggle`: return { _type: `${PKG}CommandVolumeApplicationDeviceToggle`, processes: [], followFocus: false, devices: [] } as any;
    case `${PKG}CommandObsMuteSource`: return { _type: `${PKG}CommandObsMuteSource`, source: '', type: 'toggle' as MuteType } as any;
    case `${PKG}CommandObsSetScene`: return { _type: `${PKG}CommandObsSetScene`, scene: '' } as any;
    case `${PKG}CommandObsSetSourceVolume`: return { _type: `${PKG}CommandObsSetSourceVolume`, sourceName: '', dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVoiceMeeterBasic`: return { _type: `${PKG}CommandVoiceMeeterBasic`, ct: 'STRIP', index: 0, dt: 'VOLUME', dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVoiceMeeterBasicButton`: return { _type: `${PKG}CommandVoiceMeeterBasicButton`, ct: 'STRIP', index: 0, bt: 'MONO' } as any;
    case `${PKG}CommandVoiceMeeterAdvanced`: return { _type: `${PKG}CommandVoiceMeeterAdvanced`, fullParam: '', ct: 'ABSOLUTE', dialParams: { ...DEFAULT_DIAL_PARAMS } } as any;
    case `${PKG}CommandVoiceMeeterAdvancedButton`: return { _type: `${PKG}CommandVoiceMeeterAdvancedButton`, fullParam: '', bt: 'SET', stringValue: null } as any;
    default: return { _type: `${PKG}CommandNoOp` } as any;
  }
}

@Component({
  selector: 'app-command-config',
  standalone: true,
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, MatIconModule, AudioPickerComponent, DialParamsEditorComponent],
  templateUrl: './command-config.component.html',
  styleUrl: './command-config.component.scss',
})
export class CommandConfigComponent implements OnInit {
  availableTypes: CommandTypeDef[] = [];
  selectedType = '';
  cmd: Record<string, any> = {};
  muteTypes: MuteType[] = ['mute', 'unmute', 'toggle'];
  vmControlTypes = ['STRIP', 'BUS'];
  vmDialTypes = ['VOLUME', 'GAIN', 'COMP', 'GATE', 'EQ'];
  vmButtonTypes = ['MONO', 'MUTE', 'SOLO'];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: CommandDialogData,
    private dialogRef: MatDialogRef<CommandConfigComponent>,
  ) {}

  ngOnInit(): void {
    this.availableTypes = COMMAND_TYPE_DEFS.filter(t => t.kinds.includes(this.data.kind));
    const existing = this.data.currentCommands?.commands?.[0];
    if (existing) { this.selectedType = existing._type; this.cmd = { ...existing } as any; }
    else { this.selectedType = this.availableTypes[0]?._type ?? ''; this.cmd = makeDefault(this.selectedType, this.data.kind) as any; }
  }

  onTypeChange(type: string): void { this.cmd = makeDefault(type, this.data.kind) as any; }
  is(shortName: string): boolean { return this.cmd['_type'] === `${PKG}${shortName}`; }
  asArray(val: any): string[] { return Array.isArray(val) ? val : (val ? [...val] : []); }
  removeItem(arr: any[], index: number): void { arr.splice(index, 1); }

  save(): void {
    const command = this.cmd['_type'] === `${PKG}CommandNoOp` ? null : this.cmd as Command;
    this.dialogRef.close({ commands: command ? [command] : [], type: 'allAtOnce' } as CommandsWrapper);
  }

  cancel(): void { this.dialogRef.close(undefined); }
}
