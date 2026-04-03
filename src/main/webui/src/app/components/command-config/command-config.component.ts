import {
  Component, EventEmitter, Input, OnChanges, Output, SimpleChanges
} from '@angular/core';
import { CommonModule } from '@angular/common';
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
  imports: [CommonModule, FormsModule, AudioPickerComponent, DialParamsEditorComponent],
  template: `
<div class="modal-overlay" (click)="onOverlayClick($event)">
  <div class="modal-box">
    <div class="modal-header">
      <h2>Configure {{ kind === 'dial' ? 'Dial' : 'Button' }} {{ index + 1 }}</h2>
      <button class="close-btn" (click)="cancel()" type="button">✕</button>
    </div>

    <div class="modal-body" *ngIf="cmd">
      <div class="field-row">
        <label>Action Type</label>
        <select [(ngModel)]="selectedType" (ngModelChange)="onTypeChange($event)">
          <option *ngFor="let t of availableTypes" [value]="t._type">{{ t.label }}</option>
        </select>
      </div>

      <!-- CommandMedia -->
      <ng-container *ngIf="is('CommandMedia')">
        <div class="field-row">
          <label>Media Button</label>
          <select [(ngModel)]="cmd['button']">
            <option value="playPause">Play/Pause</option>
            <option value="next">Next</option>
            <option value="prev">Previous</option>
            <option value="stop">Stop</option>
            <option value="mute">Mute</option>
          </select>
        </div>
        <div class="field-row checkbox-row">
          <label><input type="checkbox" [(ngModel)]="cmd['spotify']" /> Spotify only</label>
        </div>
      </ng-container>

      <!-- CommandKeystroke -->
      <ng-container *ngIf="is('CommandKeystroke')">
        <div class="field-row">
          <label>Keystroke</label>
          <input type="text" [(ngModel)]="cmd['keystroke']" placeholder="e.g. ctrl+shift+m" />
        </div>
      </ng-container>

      <!-- CommandRun -->
      <ng-container *ngIf="is('CommandRun')">
        <div class="field-row">
          <label>Command</label>
          <input type="text" [(ngModel)]="cmd['command']" placeholder="Path or shell command" />
        </div>
      </ng-container>

      <!-- CommandShortcut -->
      <ng-container *ngIf="is('CommandShortcut')">
        <div class="field-row">
          <label>Shortcut</label>
          <input type="text" [(ngModel)]="cmd['shortcut']" placeholder="e.g. notepad.exe" />
        </div>
      </ng-container>

      <!-- CommandEndProgram -->
      <ng-container *ngIf="is('CommandEndProgram')">
        <div class="field-row checkbox-row">
          <label><input type="checkbox" [(ngModel)]="cmd['specific']" /> Specific process</label>
        </div>
        <div class="field-row" *ngIf="cmd['specific']">
          <label>Process name</label>
          <app-audio-picker mode="process" [(value)]="cmd['name']" />
        </div>
      </ng-container>

      <!-- CommandProfile -->
      <ng-container *ngIf="is('CommandProfile')">
        <div class="field-row">
          <label>Profile name</label>
          <input type="text" [(ngModel)]="cmd['profile']" placeholder="Profile name" />
        </div>
      </ng-container>

      <!-- CommandVolumeFocusMute -->
      <ng-container *ngIf="is('CommandVolumeFocusMute')">
        <div class="field-row">
          <label>Mute action</label>
          <select [(ngModel)]="cmd['muteType']"><option *ngFor="let m of muteTypes" [value]="m">{{ m }}</option></select>
        </div>
      </ng-container>

      <!-- CommandVolumeDevice (dial) -->
      <ng-container *ngIf="is('CommandVolumeDevice')">
        <div class="field-row">
          <label>Audio Device</label>
          <app-audio-picker mode="device" [(value)]="cmd['deviceId']" />
        </div>
        <div class="field-row checkbox-row">
          <label><input type="checkbox" [(ngModel)]="cmd['isUnMuteOnVolumeChange']" /> Unmute on volume change</label>
        </div>
        <app-dial-params-editor [params]="cmd['dialParams']" />
      </ng-container>

      <!-- CommandVolumeDeviceMute -->
      <ng-container *ngIf="is('CommandVolumeDeviceMute')">
        <div class="field-row">
          <label>Audio Device</label>
          <app-audio-picker mode="device" [(value)]="cmd['deviceId']" />
        </div>
        <div class="field-row">
          <label>Mute action</label>
          <select [(ngModel)]="cmd['muteType']"><option *ngFor="let m of muteTypes" [value]="m">{{ m }}</option></select>
        </div>
      </ng-container>

      <!-- CommandVolumeDefaultDevice -->
      <ng-container *ngIf="is('CommandVolumeDefaultDevice')">
        <div class="field-row">
          <label>Audio Device</label>
          <app-audio-picker mode="device" [(value)]="cmd['deviceId']" />
        </div>
      </ng-container>

      <!-- CommandVolumeDefaultDeviceToggle -->
      <ng-container *ngIf="is('CommandVolumeDefaultDeviceToggle')">
        <div class="field-row">
          <label>Devices (cycle through)</label>
          <div class="multi-device">
            <div *ngFor="let d of cmd['devices']; let i = index" class="multi-device-row">
              <app-audio-picker mode="device" [value]="cmd['devices'][i]" (valueChange)="cmd['devices'][i] = $event" />
              <button type="button" class="btn-icon" (click)="removeItem(cmd['devices'], i)">✕</button>
            </div>
            <button type="button" class="btn-small" (click)="cmd['devices'].push('')">+ Add Device</button>
          </div>
        </div>
      </ng-container>

      <!-- CommandVolumeDefaultDeviceAdvanced -->
      <ng-container *ngIf="is('CommandVolumeDefaultDeviceAdvanced')">
        <div class="field-row">
          <label>Label</label>
          <input type="text" [(ngModel)]="cmd['name']" />
        </div>
        <div class="field-row">
          <label>Media Playback</label>
          <app-audio-picker mode="device" deviceFilter="output" [(value)]="cmd['mediaPb']" />
        </div>
        <div class="field-row">
          <label>Media Record</label>
          <app-audio-picker mode="device" deviceFilter="input" [(value)]="cmd['mediaRec']" />
        </div>
        <div class="field-row">
          <label>Comm. Playback</label>
          <app-audio-picker mode="device" deviceFilter="output" [(value)]="cmd['communicationPb']" />
        </div>
        <div class="field-row">
          <label>Comm. Record</label>
          <app-audio-picker mode="device" deviceFilter="input" [(value)]="cmd['communicationRec']" />
        </div>
      </ng-container>

      <!-- CommandVolumeProcess (dial) -->
      <ng-container *ngIf="is('CommandVolumeProcess')">
        <div class="field-row">
          <label>Processes</label>
          <div class="multi-process">
            <div *ngFor="let p of cmd['processName']; let i = index" class="multi-process-row">
              <app-audio-picker mode="process" [value]="cmd['processName'][i]" (valueChange)="cmd['processName'][i] = $event" />
              <button type="button" class="btn-icon" (click)="removeItem(cmd['processName'], i)">✕</button>
            </div>
            <button type="button" class="btn-small" (click)="cmd['processName'].push('')">+ Add Process</button>
          </div>
        </div>
        <div class="field-row">
          <label>Device</label>
          <app-audio-picker mode="device" [(value)]="cmd['device']" placeholder="Default device" />
        </div>
        <div class="field-row checkbox-row">
          <label><input type="checkbox" [(ngModel)]="cmd['isUnMuteOnVolumeChange']" /> Unmute on volume change</label>
        </div>
        <app-dial-params-editor [params]="cmd['dialParams']" />
      </ng-container>

      <!-- CommandVolumeProcessMute -->
      <ng-container *ngIf="is('CommandVolumeProcessMute')">
        <div class="field-row">
          <label>Processes</label>
          <div class="multi-process">
            <div *ngFor="let p of cmd['processName']; let i = index" class="multi-process-row">
              <app-audio-picker mode="process" [value]="asArray(cmd['processName'])[i]" (valueChange)="asArray(cmd['processName'])[i] = $event" />
              <button type="button" class="btn-icon" (click)="removeItem(asArray(cmd['processName']), i)">✕</button>
            </div>
            <button type="button" class="btn-small" (click)="asArray(cmd['processName']).push('')">+ Add Process</button>
          </div>
        </div>
        <div class="field-row">
          <label>Mute action</label>
          <select [(ngModel)]="cmd['muteType']"><option *ngFor="let m of muteTypes" [value]="m">{{ m }}</option></select>
        </div>
      </ng-container>

      <!-- CommandVolumeFocus (dial) + CommandBrightness (dial) — just dial params -->
      <ng-container *ngIf="is('CommandVolumeFocus') || is('CommandBrightness')">
        <app-dial-params-editor [params]="cmd['dialParams']" />
      </ng-container>

      <!-- CommandObsMuteSource -->
      <ng-container *ngIf="is('CommandObsMuteSource')">
        <div class="field-row">
          <label>Source name</label>
          <input type="text" [(ngModel)]="cmd['source']" />
        </div>
        <div class="field-row">
          <label>Mute action</label>
          <select [(ngModel)]="cmd['type']"><option *ngFor="let m of muteTypes" [value]="m">{{ m }}</option></select>
        </div>
      </ng-container>

      <!-- CommandObsSetScene -->
      <ng-container *ngIf="is('CommandObsSetScene')">
        <div class="field-row">
          <label>Scene name</label>
          <input type="text" [(ngModel)]="cmd['scene']" />
        </div>
      </ng-container>

      <!-- CommandObsSetSourceVolume (dial) -->
      <ng-container *ngIf="is('CommandObsSetSourceVolume')">
        <div class="field-row">
          <label>Source name</label>
          <input type="text" [(ngModel)]="cmd['sourceName']" />
        </div>
        <app-dial-params-editor [params]="cmd['dialParams']" />
      </ng-container>

      <!-- CommandVoiceMeeterBasic (dial) -->
      <ng-container *ngIf="is('CommandVoiceMeeterBasic')">
        <div class="field-row">
          <label>Control type</label>
          <select [(ngModel)]="cmd['ct']"><option *ngFor="let t of vmControlTypes" [value]="t">{{ t }}</option></select>
        </div>
        <div class="field-row">
          <label>Index</label>
          <input type="number" [(ngModel)]="cmd['index']" min="0" max="8" />
        </div>
        <div class="field-row">
          <label>Dial type</label>
          <select [(ngModel)]="cmd['dt']"><option *ngFor="let t of vmDialTypes" [value]="t">{{ t }}</option></select>
        </div>
        <app-dial-params-editor [params]="cmd['dialParams']" />
      </ng-container>

      <!-- CommandVoiceMeeterBasicButton -->
      <ng-container *ngIf="is('CommandVoiceMeeterBasicButton')">
        <div class="field-row">
          <label>Control type</label>
          <select [(ngModel)]="cmd['ct']"><option *ngFor="let t of vmControlTypes" [value]="t">{{ t }}</option></select>
        </div>
        <div class="field-row">
          <label>Index</label>
          <input type="number" [(ngModel)]="cmd['index']" min="0" max="8" />
        </div>
        <div class="field-row">
          <label>Button type</label>
          <select [(ngModel)]="cmd['bt']"><option *ngFor="let t of vmButtonTypes" [value]="t">{{ t }}</option></select>
        </div>
      </ng-container>

      <!-- CommandVoiceMeeterAdvanced (dial) -->
      <ng-container *ngIf="is('CommandVoiceMeeterAdvanced')">
        <div class="field-row">
          <label>Parameter</label>
          <input type="text" [(ngModel)]="cmd['fullParam']" placeholder="e.g. Strip[0].Gain" />
        </div>
        <div class="field-row">
          <label>Control mode</label>
          <select [(ngModel)]="cmd['ct']">
            <option value="ABSOLUTE">Absolute</option>
            <option value="RELATIVE">Relative</option>
          </select>
        </div>
        <app-dial-params-editor [params]="cmd['dialParams']" />
      </ng-container>

      <!-- CommandVoiceMeeterAdvancedButton -->
      <ng-container *ngIf="is('CommandVoiceMeeterAdvancedButton')">
        <div class="field-row">
          <label>Parameter</label>
          <input type="text" [(ngModel)]="cmd['fullParam']" placeholder="e.g. Strip[0].Mute" />
        </div>
        <div class="field-row">
          <label>Button mode</label>
          <select [(ngModel)]="cmd['bt']">
            <option value="SET">Set value</option>
            <option value="TOGGLE">Toggle</option>
          </select>
        </div>
        <div class="field-row" *ngIf="cmd['bt'] === 'SET'">
          <label>Value</label>
          <input type="text" [(ngModel)]="cmd['stringValue']" placeholder="e.g. 1" />
        </div>
      </ng-container>

    </div><!-- /modal-body -->

    <div class="modal-footer">
      <button type="button" class="btn btn-secondary" (click)="cancel()">Cancel</button>
      <button type="button" class="btn btn-primary" (click)="save()">Save</button>
    </div>
  </div>
</div>
  `,
  styles: [`
    .modal-overlay {
      position: fixed; inset: 0; background: rgba(0,0,0,.65);
      display: flex; align-items: center; justify-content: center; z-index: 1000;
    }
    .modal-box {
      background: #1e1e1e; border: 1px solid #444; border-radius: 8px;
      width: min(560px, 96vw); max-height: 90vh; display: flex; flex-direction: column;
      box-shadow: 0 8px 32px rgba(0,0,0,.5);
    }
    .modal-header {
      display: flex; justify-content: space-between; align-items: center;
      padding: 14px 18px; border-bottom: 1px solid #333;
    }
    .modal-header h2 { margin: 0; font-size: 16px; color: #eee; }
    .close-btn { background: none; border: none; color: #aaa; font-size: 18px; cursor: pointer; padding: 0 4px; }
    .close-btn:hover { color: #fff; }
    .modal-body { padding: 16px 18px; overflow-y: auto; flex: 1; display: flex; flex-direction: column; gap: 10px; }
    .modal-footer { padding: 12px 18px; border-top: 1px solid #333; display: flex; justify-content: flex-end; gap: 8px; }
    .field-row { display: flex; flex-direction: column; gap: 4px; }
    .field-row label { font-size: 12px; color: #aaa; }
    .field-row input[type=text], .field-row input[type=number], .field-row select {
      padding: 6px 8px; border: 1px solid #444; border-radius: 4px;
      background: #2a2a2a; color: #eee; font-size: 13px;
    }
    .checkbox-row label { display: flex; align-items: center; gap: 6px; color: #ccc; font-size: 13px; cursor: pointer; }
    .multi-process, .multi-device { display: flex; flex-direction: column; gap: 6px; }
    .multi-process-row, .multi-device-row { display: flex; gap: 6px; align-items: flex-start; }
    .multi-process-row app-audio-picker, .multi-device-row app-audio-picker { flex: 1; }
    .btn-icon { background: none; border: 1px solid #555; color: #aaa; border-radius: 4px; cursor: pointer; padding: 4px 8px; font-size: 14px; }
    .btn-icon:hover { background: #3a3a3a; color: #fff; }
    .btn-small { padding: 4px 10px; font-size: 12px; border: 1px dashed #555; background: none; color: #aaa; border-radius: 4px; cursor: pointer; }
    .btn-small:hover { background: #2a2a2a; color: #ccc; }
    .btn { padding: 7px 18px; border-radius: 4px; border: none; cursor: pointer; font-size: 13px; }
    .btn-primary { background: #4a90d9; color: #fff; }
    .btn-primary:hover { background: #357abd; }
    .btn-secondary { background: #333; color: #ccc; }
    .btn-secondary:hover { background: #3a3a3a; }
  `]
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
