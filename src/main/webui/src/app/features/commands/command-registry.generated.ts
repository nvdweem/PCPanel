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
}

export const GENERATED_COMMANDS: GeneratedCommand[] = [
  { type: 'com.getpcpanel.commands.command.CommandAnalogBands', label: 'Stepped switch (ranges)', category: 'system', kinds: ['dial'], icon: 'sliders' },
  { type: 'com.getpcpanel.commands.command.CommandBrightness', label: 'Brightness', category: 'system', kinds: ['dial'], icon: 'sun' },
  { type: 'com.getpcpanel.commands.command.CommandEndProgram', label: 'End program', category: 'system', kinds: ['button'], icon: 'x' },
  { type: 'com.getpcpanel.commands.command.CommandHttpRequest', label: 'HTTP request', category: 'system', kinds: ['dial', 'button'], icon: 'zap' },
  { type: 'com.getpcpanel.commands.command.CommandKeystroke', label: 'Keystroke', category: 'system', kinds: ['button'], icon: 'keyboard' },
  { type: 'com.getpcpanel.commands.command.CommandMedia', label: 'Media', category: 'system', kinds: ['button'], icon: 'play' },
  { type: 'com.getpcpanel.commands.command.CommandMqttPublish', label: 'MQTT publish', category: 'system', kinds: ['dial', 'button'], icon: 'zap' },
  { type: 'com.getpcpanel.commands.command.CommandObsAction', label: 'OBS — stream / record', category: 'integration', kinds: ['button'], integration: 'obs', icon: 'film' },
  { type: 'com.getpcpanel.commands.command.CommandObsMuteSource', label: 'OBS — mute source', category: 'integration', kinds: ['button'], integration: 'obs', icon: 'mic-off' },
  { type: 'com.getpcpanel.commands.command.CommandObsSetScene', label: 'OBS — switch scene', category: 'integration', kinds: ['button'], integration: 'obs', icon: 'film' },
  { type: 'com.getpcpanel.commands.command.CommandObsSetSourceVolume', label: 'OBS — source volume', category: 'integration', kinds: ['dial'], integration: 'obs', icon: 'sliders' },
  { type: 'com.getpcpanel.commands.command.CommandOscSend', label: 'OSC send', category: 'system', kinds: ['dial', 'button'], icon: 'sliders' },
  { type: 'com.getpcpanel.commands.command.CommandProfile', label: 'Switch profile', category: 'system', kinds: ['button'], icon: 'refresh' },
  { type: 'com.getpcpanel.commands.command.CommandRun', label: 'Run command', category: 'system', kinds: ['button'], icon: 'zap' },
  { type: 'com.getpcpanel.commands.command.CommandShortcut', label: 'Run shortcut', category: 'system', kinds: ['button'], icon: 'zap' },
  { type: 'com.getpcpanel.commands.command.CommandVoiceMeeterAdvanced', label: 'Voicemeeter — parameter', category: 'integration', kinds: ['dial'], integration: 'voicemeeter', icon: 'sliders' },
  { type: 'com.getpcpanel.commands.command.CommandVoiceMeeterAdvancedButton', label: 'Voicemeeter — button', category: 'integration', kinds: ['button'], integration: 'voicemeeter', icon: 'sliders' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeDefaultDevice', label: 'Set default device', category: 'audio', kinds: ['button'], icon: 'monitor' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeDefaultDeviceAdvanced', label: 'Advanced default device', category: 'audio', kinds: ['button'], icon: 'monitor' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeDefaultDeviceToggle', label: 'Cycle default device', category: 'audio', kinds: ['button'], icon: 'refresh' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeDevice', label: 'Device volume', category: 'audio', kinds: ['dial'], icon: 'volume' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeDeviceMute', label: 'Device mute', category: 'audio', kinds: ['button'], icon: 'volume-x' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeFocus', label: 'Focused-app volume', category: 'audio', kinds: ['dial'], icon: 'volume' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeFocusMute', label: 'Focused-app mute', category: 'audio', kinds: ['button'], icon: 'volume-x' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeProcess', label: 'App volume', category: 'audio', kinds: ['dial'], icon: 'volume' },
  { type: 'com.getpcpanel.commands.command.CommandVolumeProcessMute', label: 'App mute', category: 'audio', kinds: ['button'], icon: 'volume-x' },
  { type: 'com.getpcpanel.discord.command.CommandDiscordJoinVoice', label: 'Discord — join voice', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'plug' },
  { type: 'com.getpcpanel.discord.command.CommandDiscordLeaveVoice', label: 'Discord — leave voice', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'log-out' },
  { type: 'com.getpcpanel.discord.command.CommandDiscordMute', label: 'Discord — mute', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'mic-off' },
  { type: 'com.getpcpanel.discord.command.CommandDiscordScreenShare', label: 'Discord — screen share', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'monitor' },
  { type: 'com.getpcpanel.discord.command.CommandDiscordSelfDeafen', label: 'Discord — deafen self', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'volume-x' },
  { type: 'com.getpcpanel.discord.command.CommandDiscordToggleVideo', label: 'Discord — toggle camera', category: 'integration', kinds: ['button'], integration: 'discord', icon: 'film' },
  { type: 'com.getpcpanel.discord.command.CommandDiscordVolume', label: 'Discord — volume', category: 'integration', kinds: ['dial'], integration: 'discord', icon: 'volume' },
  { type: 'com.getpcpanel.homeassistant.command.CommandHomeAssistantAction', label: 'Home Assistant — perform action', category: 'integration', kinds: ['button'], integration: 'homeassistant', icon: 'zap' },
  { type: 'com.getpcpanel.homeassistant.command.CommandHomeAssistantValue', label: 'Home Assistant — set value', category: 'integration', kinds: ['dial'], integration: 'homeassistant', icon: 'sliders' },
  { type: 'com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel', label: 'Wave Link — add focused app', category: 'integration', kinds: ['button'], integration: 'wavelink', icon: 'plus' },
  { type: 'com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel', label: 'Wave Link — level', category: 'integration', kinds: ['dial'], integration: 'wavelink', icon: 'sliders' },
  { type: 'com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute', label: 'Wave Link — mute', category: 'integration', kinds: ['button'], integration: 'wavelink', icon: 'mic-off' },
  { type: 'com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput', label: 'Wave Link — main output', category: 'integration', kinds: ['button'], integration: 'wavelink', icon: 'volume' },
];
