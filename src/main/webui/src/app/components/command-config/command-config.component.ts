import { Component, Inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { AudioPickerComponent } from '../audio-picker/audio-picker.component';
import { DialParamsEditorComponent } from './dial-params-editor.component';
import { Commands, Kinds, MuteType } from '../../models/generated/backend.types';

type CommandTypeDef = {
  _type: string;
  kinds: string[];
  label: string;
}; // TODO

const COMMAND_TYPE_DEFS: CommandTypeDef[] = [];

export interface CommandDialogData {
  kind: Kinds;
  index: number;
  currentCommands: Commands | null;
  profiles: string[];
}

@Component({
  selector: 'app-command-config',
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
  ) {
  }

  ngOnInit(): void {
    this.availableTypes = COMMAND_TYPE_DEFS.filter(t => t.kinds.includes(this.data.kind));
    const existing = this.data.currentCommands?.commands?.[0];
    if (existing) {
      this.selectedType = existing._type;
      this.cmd = {...existing} as any;
    } else {
      this.selectedType = this.availableTypes[0]?._type ?? '';
      // this.cmd = makeDefault(this.selectedType, this.data.kind) as any;
    }
  }

  onTypeChange(type: string): void {
    // this.cmd = makeDefault(type, this.data.kind) as any;
  }

  is(shortName: string): boolean {
    return false;
    // return this.cmd['_type'] === `${PKG}${shortName}`;
  }

  asArray(val: any): string[] {
    return Array.isArray(val) ? val : (val ? [...val] : []);
  }

  removeItem(arr: any[], index: number): void {
    arr.splice(index, 1);
  }

  save(): void {
    // const command = this.cmd['_type'] === `${PKG}CommandNoOp` ? null : this.cmd as Command;
    // this.dialogRef.close({commands: command ? [command] : [], type: 'All at once'} as Commands);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
