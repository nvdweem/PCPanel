import {
  CommandBrightness,
  CommandEndProgram,
  CommandKeystroke,
  CommandMedia,
  CommandObsMuteSource,
  CommandObsSetScene,
  CommandObsSetSourceVolume,
  CommandRun,
  CommandShortcut,
  CommandType,
  CommandVoiceMeeterAdvanced,
  CommandVoiceMeeterAdvancedButton,
  CommandVoiceMeeterBasic,
  CommandVoiceMeeterBasicButton,
  CommandVolumeApplicationDeviceToggle,
  CommandVolumeDefaultDevice,
  CommandVolumeDefaultDeviceAdvanced,
  CommandVolumeDefaultDeviceToggle,
  CommandVolumeDefaultDeviceToggleAdvanced,
  CommandVolumeDevice,
  CommandVolumeDeviceMute,
  CommandVolumeFocus,
  CommandVolumeFocusMute,
  CommandVolumeProcess,
  CommandVolumeProcessMute,
  CommandWaveLinkAddFocusToChannel,
  CommandWaveLinkChangeLevel,
  CommandWaveLinkChangeMute,
  CommandWaveLinkChannelEffect,
  CommandWaveLinkMainOutput,
  DialCommandParams
} from '../../../models/generated/backend.types';
import { CommandVolumeFocusComponent } from './command-volume-focus-component/command-volume-focus-component';
import { CommandVolumeDeviceMuteComponent } from './command-volume-device-mute-component/command-volume-device-mute-component';
import { CommandBrightnessComponent } from './command-brightness-component/command-brightness-component';
import { CommandKeystrokeComponent } from './command-keystroke-component/command-keystroke-component';
import { CommandRunComponent } from './command-run-component/command-run-component';
import { CommandShortcutComponent } from './command-shortcut-component/command-shortcut-component';
import { CommandMediaComponent } from './command-media-component/command-media-component';
import { CommandEndProgramComponent } from './command-end-program-component/command-end-program-component';
import { CommandVolumeDefaultDeviceComponent } from './command-volume-default-device-component/command-volume-default-device-component';
import { CommandVolumeDefaultDeviceAdvancedComponent } from './command-volume-default-device-advanced-component/command-volume-default-device-advanced-component';
import { CommandVolumeDefaultDeviceToggleComponent } from './command-volume-default-device-toggle-component/command-volume-default-device-toggle-component';
import { CommandVolumeDefaultDeviceToggleAdvancedComponent } from './command-volume-default-device-toggle-advanced-component/command-volume-default-device-toggle-advanced-component';
import { CommandVolumeFocusMuteComponent } from './command-volume-focus-mute-component/command-volume-focus-mute-component';
import { CommandVolumeProcessMuteComponent } from './command-volume-process-mute-component/command-volume-process-mute-component';
import { CommandVolumeProcessComponent } from './command-volume-process-component/command-volume-process-component';
import { CommandVolumeApplicationDeviceToggleComponent } from './command-volume-application-device-toggle-component/command-volume-application-device-toggle-component';
import { CommandObsSetSceneComponent } from './command-obs-set-scene-component/command-obs-set-scene-component';
import { CommandObsMuteSourceComponent } from './command-obs-mute-source-component/command-obs-mute-source-component';
import { CommandObsSetSourceVolumeComponent } from './command-obs-set-source-volume-component/command-obs-set-source-volume-component';
import { CommandVoiceMeeterBasicComponent } from './command-voicemeeter-basic-component/command-voicemeeter-basic-component';
import { CommandVoiceMeeterAdvancedComponent } from './command-voicemeeter-advanced-component/command-voicemeeter-advanced-component';
import { CommandVoiceMeeterBasicButtonComponent } from './command-voicemeeter-basic-button-component/command-voicemeeter-basic-button-component';
import { CommandVoiceMeeterAdvancedButtonComponent } from './command-voicemeeter-advanced-button-component/command-voicemeeter-advanced-button-component';
import { CommandWaveLinkChangeLevelComponent } from './command-wavelink-change-level-component/command-wavelink-change-level-component';
import { CommandWaveLinkAddFocusToChannelComponent } from './command-wavelink-add-focus-to-channel-component/command-wavelink-add-focus-to-channel-component';
import { CommandWaveLinkChangeMuteComponent } from './command-wavelink-change-mute-component/command-wavelink-change-mute-component';
import { CommandWaveLinkChannelEffectComponent } from './command-wavelink-channel-effect-component/command-wavelink-channel-effect-component';
import { CommandWaveLinkMainOutputComponent } from './command-wavelink-main-output-component/command-wavelink-main-output-component';
import { CommandVolumeDeviceComponent } from './command-volume-device-component/command-volume-device-component';
import { Type } from '@angular/core';

export function validateCommands(command: CommandType[]) {
  const withoutComponent = command.filter(c => !commandComponentMap.has(c.command));
  if (withoutComponent.length > 0) {
    console.error(`No component for commands: ${withoutComponent.map(c => c.command).join(', ')}`);
  }
}

interface CommandComponentDefinition<T> {
  component: Type<any>
  buildEmpty: () => Required<T>,
}

export const commandComponentMap = new Map<string, CommandComponentDefinition<unknown>>([
  ['com.getpcpanel.commands.command.CommandVolumeFocus', {component: CommandVolumeFocusComponent, buildEmpty: buildEmptyCommandVolumeFocus}],
  ['com.getpcpanel.commands.command.CommandVolumeDeviceMute', {component: CommandVolumeDeviceMuteComponent, buildEmpty: buildEmptyCommandVolumeDeviceMute}],
  ['com.getpcpanel.commands.command.CommandBrightness', {component: CommandBrightnessComponent, buildEmpty: buildEmptyCommandBrightness}],
  ['com.getpcpanel.commands.command.CommandKeystroke', {component: CommandKeystrokeComponent, buildEmpty: buildEmptyCommandKeystroke}],
  ['com.getpcpanel.commands.command.CommandRun', {component: CommandRunComponent, buildEmpty: buildEmptyCommandRun}],
  ['com.getpcpanel.commands.command.CommandShortcut', {component: CommandShortcutComponent, buildEmpty: buildEmptyCommandShortcut}],
  ['com.getpcpanel.commands.command.CommandMedia', {component: CommandMediaComponent, buildEmpty: buildEmptyCommandMedia}],
  ['com.getpcpanel.commands.command.CommandEndProgram', {component: CommandEndProgramComponent, buildEmpty: buildEmptyCommandEndProgram}],
  ['com.getpcpanel.commands.command.CommandVolumeDefaultDevice', {component: CommandVolumeDefaultDeviceComponent, buildEmpty: buildEmptyCommandVolumeDefaultDevice}],
  ['com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced', {component: CommandVolumeDefaultDeviceAdvancedComponent, buildEmpty: buildEmptyCommandVolumeDefaultDeviceAdvanced}],
  ['com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle', {component: CommandVolumeDefaultDeviceToggleComponent, buildEmpty: buildEmptyCommandVolumeDefaultDeviceToggle}],
  ['com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced', {
    component: CommandVolumeDefaultDeviceToggleAdvancedComponent,
    buildEmpty: buildEmptyCommandVolumeDefaultDeviceToggleAdvanced
  }],
  ['com.getpcpanel.commands.command.CommandVolumeDevice', {component: CommandVolumeDeviceComponent, buildEmpty: buildEmptyCommandVolumeDevice}],
  ['com.getpcpanel.commands.command.CommandVolumeFocusMute', {component: CommandVolumeFocusMuteComponent, buildEmpty: buildEmptyCommandVolumeFocusMute}],
  ['com.getpcpanel.commands.command.CommandVolumeProcessMute', {component: CommandVolumeProcessMuteComponent, buildEmpty: buildEmptyCommandVolumeProcessMute}],
  ['com.getpcpanel.commands.command.CommandVolumeProcess', {component: CommandVolumeProcessComponent, buildEmpty: buildEmptyCommandVolumeProcess}],
  ['com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle', {component: CommandVolumeApplicationDeviceToggleComponent, buildEmpty: buildEmptyCommandVolumeApplicationDeviceToggle}],
  ['com.getpcpanel.commands.command.CommandObsSetScene', {component: CommandObsSetSceneComponent, buildEmpty: buildEmptyCommandObsSetScene}],
  ['com.getpcpanel.commands.command.CommandObsMuteSource', {component: CommandObsMuteSourceComponent, buildEmpty: buildEmptyCommandObsMuteSource}],
  ['com.getpcpanel.commands.command.CommandObsSetSourceVolume', {component: CommandObsSetSourceVolumeComponent, buildEmpty: buildEmptyCommandObsSetSourceVolume}],
  ['com.getpcpanel.commands.command.CommandVoiceMeeterBasic', {component: CommandVoiceMeeterBasicComponent, buildEmpty: buildEmptyCommandVoiceMeeterBasic}],
  ['com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced', {component: CommandVoiceMeeterAdvancedComponent, buildEmpty: buildEmptyCommandVoiceMeeterAdvanced}],
  ['com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton', {component: CommandVoiceMeeterBasicButtonComponent, buildEmpty: buildEmptyCommandVoiceMeeterBasicButton}],
  ['com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton', {component: CommandVoiceMeeterAdvancedButtonComponent, buildEmpty: buildEmptyCommandVoiceMeeterAdvancedButton}],
  ['com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel', {component: CommandWaveLinkChangeLevelComponent, buildEmpty: buildEmptyCommandWaveLinkChangeLevel}],
  ['com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel', {component: CommandWaveLinkAddFocusToChannelComponent, buildEmpty: buildEmptyCommandWaveLinkAddFocusToChannel}],
  ['com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute', {component: CommandWaveLinkChangeMuteComponent, buildEmpty: buildEmptyCommandWaveLinkChangeMute}],
  ['com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect', {component: CommandWaveLinkChannelEffectComponent, buildEmpty: buildEmptyCommandWaveLinkChannelEffect}],
  ['com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput', {component: CommandWaveLinkMainOutputComponent, buildEmpty: buildEmptyCommandWaveLinkMainOutput}],
]);

export function buildEmptyDialCommandParams(): DialCommandParams {
  return {
    invert: false,
    moveStart: 0,
    moveEnd: 100,
  };
}

function buildEmptyCommandVolumeFocus(): Required<CommandVolumeFocus> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeFocus',
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandVolumeDeviceMute(): Required<CommandVolumeDeviceMute> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeDeviceMute',
    deviceId: '',
    muteType: 'toggle',
    overlayText: '',
  };
}

function buildEmptyCommandVolumeDevice(): Required<CommandVolumeDevice> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeDevice',
    deviceId: '',
    isUnMuteOnVolumeChange: false,
    unMuteOnVolumeChange: false,
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandBrightness(): Required<CommandBrightness> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandBrightness',
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandKeystroke(): Required<CommandKeystroke> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandKeystroke',
    keystroke: '',
    overlayText: '',
  };
}

function buildEmptyCommandRun(): Required<CommandRun> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandRun',
    command: '',
    overlayText: '',
  };
}

function buildEmptyCommandShortcut(): Required<CommandShortcut> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandShortcut',
    shortcut: '',
    overlayText: '',
  };
}

function buildEmptyCommandMedia(): Required<CommandMedia> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandMedia',
    button: 'mute',
    spotify: false,
    overlayText: '',
  };
}

function buildEmptyCommandEndProgram(): Required<CommandEndProgram> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandEndProgram',
    name: '',
    specific: false,
    overlayText: '',
  };
}

function buildEmptyCommandVolumeDefaultDevice(): Required<CommandVolumeDefaultDevice> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeDefaultDevice',
    deviceId: '',
    overlayText: '',
  };
}

function buildEmptyCommandVolumeDefaultDeviceAdvanced(): Required<CommandVolumeDefaultDeviceAdvanced> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced',
    communicationPb: '',
    communicationRec: '',
    mediaPb: '',
    mediaRec: '',
    name: '',
    overlayText: '',
  };
}

function buildEmptyCommandVolumeDefaultDeviceToggle(): Required<CommandVolumeDefaultDeviceToggle> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle',
    currentIdx: 0,
    devices: [],
    overlayText: '',
  };
}

function buildEmptyCommandVolumeDefaultDeviceToggleAdvanced(): Required<CommandVolumeDefaultDeviceToggleAdvanced> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggleAdvanced',
    currentIdx: 0,
    devices: [],
    overlayText: '',
  };
}

function buildEmptyCommandVolumeFocusMute(): Required<CommandVolumeFocusMute> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeFocusMute',
    muteType: 'toggle',
    overlayText: '',
  };
}

function buildEmptyCommandVolumeProcessMute(): Required<CommandVolumeProcessMute> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeProcessMute',
    muteType: 'toggle',
    processName: [],
    overlayText: '',
  };
}

function buildEmptyCommandVolumeProcess(): Required<CommandVolumeProcess> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeProcess',
    device: '',
    isUnMuteOnVolumeChange: false,
    processName: [],
    unMuteOnVolumeChange: false,
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandVolumeApplicationDeviceToggle(): Required<CommandVolumeApplicationDeviceToggle> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVolumeApplicationDeviceToggle',
    currentIdx: 0,
    devices: [],
    followFocus: false,
    processes: [],
    overlayText: '',
  };
}

function buildEmptyCommandObsSetScene(): Required<CommandObsSetScene> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandObsSetScene',
    scene: '',
    overlayText: '',
  };
}

function buildEmptyCommandObsMuteSource(): Required<CommandObsMuteSource> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandObsMuteSource',
    source: '',
    type: 'toggle',
    overlayText: '',
  };
}

function buildEmptyCommandObsSetSourceVolume(): Required<CommandObsSetSourceVolume> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandObsSetSourceVolume',
    sourceName: '',
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandVoiceMeeterBasic(): Required<CommandVoiceMeeterBasic> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVoiceMeeterBasic',
    ct: 'Input',
    dt: 'Gain',
    index: 0,
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandVoiceMeeterAdvanced(): Required<CommandVoiceMeeterAdvanced> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced',
    ct: '-12 to 12',
    fullParam: '',
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandVoiceMeeterBasicButton(): Required<CommandVoiceMeeterBasicButton> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVoiceMeeterBasicButton',
    bt: 'Mute',
    ct: 'Input',
    index: 0,
    overlayText: '',
  };
}

function buildEmptyCommandVoiceMeeterAdvancedButton(): Required<CommandVoiceMeeterAdvancedButton> {
  return {
    _type: 'com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton',
    bt: 'Toggle',
    fullParam: '',
    stringValue: '',
    overlayText: '',
  };
}

function buildEmptyCommandWaveLinkChangeLevel(): Required<CommandWaveLinkChangeLevel> {
  return {
    _type: 'com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel',
    commandType: 'Input',
    id1: '',
    id2: '',
    dialParams: buildEmptyDialCommandParams(),
    invert: false,
  };
}

function buildEmptyCommandWaveLinkAddFocusToChannel(): Required<CommandWaveLinkAddFocusToChannel> {
  return {
    _type: 'com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel',
    channelId: '',
    channelName: '',
    id: '',
    name: '',
    overlayText: '',
  };
}

function buildEmptyCommandWaveLinkChangeMute(): Required<CommandWaveLinkChangeMute> {
  return {
    _type: 'com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute',
    commandType: 'Input',
    id1: '',
    id2: '',
    muteType: 'toggle',
    overlayText: '',
  };
}

function buildEmptyCommandWaveLinkChannelEffect(): Required<CommandWaveLinkChannelEffect> {
  return {
    _type: 'com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect',
    channelId: '',
    channelName: '',
    effectId: '',
    effectName: '',
    toggleType: 'toggle',
    overlayText: '',
  };
}

function buildEmptyCommandWaveLinkMainOutput(): Required<CommandWaveLinkMainOutput> {
  return {
    _type: 'com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput',
    id: '',
    name: '',
    overlayText: '',
  };
}
