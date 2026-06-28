// GENERATED FILE — do not edit. Source: @CommandMeta on the command classes
// (com.getpcpanel.**.command). Regenerate via CommandRegistryGeneratorTest.
import { IconName } from '../../ui';
import type { CommandCategory, CommandKind, Integration } from './command-catalog';

export interface GeneratedCommand {
  type: string;
  label: string;
  category: CommandCategory;
  kinds: CommandKind[];
  integration?: Integration;
  icon: IconName;
  /** previous _type id(s) for joining hand-written field schemas keyed by the old id */
  legacy?: string;
}

export const GENERATED_COMMANDS: GeneratedCommand[] = [
  { type: 'analogbands.ranges', label: 'Stepped switch (ranges)', category: 'system', kinds: ['dial'], icon: 'sliders', legacy: 'com.getpcpanel.commands.command.CommandAnalogBands' },
  { type: 'device.brightness', label: 'Brightness', category: 'system', kinds: ['dial'], icon: 'sun', legacy: 'com.getpcpanel.commands.command.CommandBrightness' },
  { type: 'discord.join-voice', label: 'Discord — join voice', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'plug', legacy: 'com.getpcpanel.discord.command.CommandDiscordJoinVoice' },
  { type: 'discord.leave-voice', label: 'Discord — leave voice', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'log-out', legacy: 'com.getpcpanel.discord.command.CommandDiscordLeaveVoice' },
  { type: 'discord.mute', label: 'Discord — mute', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'mic-off', legacy: 'com.getpcpanel.discord.command.CommandDiscordMute' },
  { type: 'discord.screen-share', label: 'Discord — screen share', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'monitor', legacy: 'com.getpcpanel.discord.command.CommandDiscordScreenShare' },
  { type: 'discord.self-deafen', label: 'Discord — deafen self', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'volume-x', legacy: 'com.getpcpanel.discord.command.CommandDiscordSelfDeafen' },
  { type: 'discord.toggle-video', label: 'Discord — toggle camera', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'film', legacy: 'com.getpcpanel.discord.command.CommandDiscordToggleVideo' },
  { type: 'discord.volume', label: 'Discord — volume', category: 'integration', kinds: ['dial'], integration: 'discord', icon: 'volume', legacy: 'com.getpcpanel.discord.command.CommandDiscordVolume' },
  { type: 'homeassistant.action', label: 'Home Assistant — perform action', category: 'integration', kinds: ['button'], integration: 'homeassistant', icon: 'zap', legacy: 'com.getpcpanel.homeassistant.command.CommandHomeAssistantAction' },
  { type: 'homeassistant.value', label: 'Home Assistant — set value', category: 'integration', kinds: ['dial'], integration: 'homeassistant', icon: 'sliders', legacy: 'com.getpcpanel.homeassistant.command.CommandHomeAssistantValue' },
  { type: 'keyboard.keystroke', label: 'Keystroke', category: 'system', kinds: ['button'], icon: 'keyboard', legacy: 'com.getpcpanel.commands.command.CommandKeystroke' },
  { type: 'keyboard.media', label: 'Media', category: 'system', kinds: ['button'], icon: 'play', legacy: 'com.getpcpanel.commands.command.CommandMedia' },
  { type: 'mqtt.publish', label: 'MQTT publish', category: 'system', kinds: ['dial', 'button'], icon: 'zap', legacy: 'com.getpcpanel.commands.command.CommandMqttPublish' },
  { type: 'obs.action', label: 'OBS — stream / record', category: 'integration', kinds: ['button'], integration: 'obs', icon: 'film', legacy: 'com.getpcpanel.commands.command.CommandObsAction' },
  { type: 'obs.mute-source', label: 'OBS — mute source', category: 'integration', kinds: ['button'], integration: 'obs', icon: 'mic-off', legacy: 'com.getpcpanel.commands.command.CommandObsMuteSource' },
  { type: 'obs.set-scene', label: 'OBS — switch scene', category: 'integration', kinds: ['button'], integration: 'obs', icon: 'film', legacy: 'com.getpcpanel.commands.command.CommandObsSetScene' },
  { type: 'obs.set-source-volume', label: 'OBS — source volume', category: 'integration', kinds: ['dial'], integration: 'obs', icon: 'sliders', legacy: 'com.getpcpanel.commands.command.CommandObsSetSourceVolume' },
  { type: 'osc.send', label: 'OSC send', category: 'system', kinds: ['dial', 'button'], icon: 'sliders', legacy: 'com.getpcpanel.commands.command.CommandOscSend' },
  { type: 'output.http-request', label: 'HTTP request', category: 'system', kinds: ['dial', 'button'], icon: 'zap', legacy: 'com.getpcpanel.commands.command.CommandHttpRequest' },
  { type: 'profile.switch', label: 'Switch profile', category: 'system', kinds: ['button'], icon: 'refresh', legacy: 'com.getpcpanel.commands.command.CommandProfile' },
  { type: 'program.end-program', label: 'End program', category: 'system', kinds: ['button'], icon: 'x', legacy: 'com.getpcpanel.commands.command.CommandEndProgram' },
  { type: 'program.run', label: 'Run command', category: 'system', kinds: ['button'], icon: 'zap', legacy: 'com.getpcpanel.commands.command.CommandRun' },
  { type: 'program.shortcut', label: 'Run shortcut', category: 'system', kinds: ['button'], icon: 'zap', legacy: 'com.getpcpanel.commands.command.CommandShortcut' },
  { type: 'voicemeeter.advanced', label: 'Voicemeeter — parameter', category: 'integration', kinds: ['dial'], integration: 'voicemeeter', icon: 'sliders', legacy: 'com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced' },
  { type: 'voicemeeter.advanced-button', label: 'Voicemeeter — button', category: 'integration', kinds: ['button'], integration: 'voicemeeter', icon: 'sliders', legacy: 'com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton' },
  { type: 'volume.default-device', label: 'Set default device', category: 'audio', kinds: ['button'], icon: 'monitor', legacy: 'com.getpcpanel.commands.command.CommandVolumeDefaultDevice' },
  { type: 'volume.default-device-advanced', label: 'Advanced default device', category: 'audio', kinds: ['button'], icon: 'monitor', legacy: 'com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced' },
  { type: 'volume.default-device-toggle', label: 'Cycle default device', category: 'audio', kinds: ['button'], icon: 'refresh', legacy: 'com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle' },
  { type: 'volume.device', label: 'Device volume', category: 'audio', kinds: ['dial'], icon: 'volume', legacy: 'com.getpcpanel.commands.command.CommandVolumeDevice' },
  { type: 'volume.device-mute', label: 'Device mute', category: 'audio', kinds: ['button'], icon: 'volume-x', legacy: 'com.getpcpanel.commands.command.CommandVolumeDeviceMute' },
  { type: 'volume.focus', label: 'Focused-app volume', category: 'audio', kinds: ['dial'], icon: 'volume', legacy: 'com.getpcpanel.commands.command.CommandVolumeFocus' },
  { type: 'volume.focus-mute', label: 'Focused-app mute', category: 'audio', kinds: ['button'], icon: 'volume-x', legacy: 'com.getpcpanel.commands.command.CommandVolumeFocusMute' },
  { type: 'volume.process', label: 'App volume', category: 'audio', kinds: ['dial'], icon: 'volume', legacy: 'com.getpcpanel.commands.command.CommandVolumeProcess' },
  { type: 'volume.process-mute', label: 'App mute', category: 'audio', kinds: ['button'], icon: 'volume-x', legacy: 'com.getpcpanel.commands.command.CommandVolumeProcessMute' },
  { type: 'wavelink.add-focus-to-channel', label: 'Wave Link — add focused app', category: 'integration', kinds: ['button'], integration: 'wavelink', icon: 'plus', legacy: 'com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel' },
  { type: 'wavelink.change-level', label: 'Wave Link — level', category: 'integration', kinds: ['dial'], integration: 'wavelink', icon: 'sliders', legacy: 'com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel' },
  { type: 'wavelink.change-mute', label: 'Wave Link — mute', category: 'integration', kinds: ['button'], integration: 'wavelink', icon: 'mic-off', legacy: 'com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute' },
  { type: 'wavelink.main-output', label: 'Wave Link — main output', category: 'integration', kinds: ['button'], integration: 'wavelink', icon: 'volume', legacy: 'com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput' },
];
