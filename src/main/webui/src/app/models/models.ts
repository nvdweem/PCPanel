// Core data models matching Java backend DTOs

export interface DeviceDto {
  serial: string;
  displayName: string;
  deviceType: 'PCPANEL_RGB' | 'PCPANEL_MINI' | 'PCPANEL_PRO';
  analogCount: number;
  buttonCount: number;
  hasLogoLed: boolean;
  currentProfile: string;
  profiles: string[];
}

export interface ProfileDto {
  name: string;
  isMainProfile: boolean;
}

export interface KnobSetting {
  minTrim: number;
  maxTrim: number;
  logarithmic: boolean;
  overlayIcon: string;
  buttonDebounce: number;
}

export interface LightingConfig {
  lightingMode: 'ALL_COLOR' | 'ALL_RAINBOW' | 'ALL_WAVE' | 'ALL_BREATH' | 'SINGLE_COLOR' | 'CUSTOM';
  individualColors: string[];
  volumeBrightnessTrackingEnabled: boolean[];
  allColor: string;
  rainbowPhaseShift: number;
  rainbowBrightness: number;
  rainbowSpeed: number;
  rainbowReverse: number;
  rainbowVertical: number;
  waveHue: number;
  waveBrightness: number;
  waveSpeed: number;
  waveReverse: number;
  waveBounce: number;
  breathHue: number;
  breathBrightness: number;
  breathSpeed: number;
  globalBrightness: number;
}

export interface AudioDevice {
  name: string;
  id: string;
  volume: number;
  muted: boolean;
  output: boolean;
  input: boolean;
}

export interface AudioSession {
  pid: number;
  title: string;
  volume: number;
  muted: boolean;
  icon: string | null;
}

export interface SettingsDto {
  mainUIIcons: boolean;
  startupVersionCheck: boolean;
  forceVolume: boolean;
  dblClickInterval: number;
  preventClickWhenDblClick: boolean;
  preventSliderTwitchDelay: number | null;
  sliderRollingAverage: number | null;
  sendOnlyIfDelta: number | null;
  workaroundsOnlySliders: boolean;
  obsEnabled: boolean;
  obsAddress: string;
  obsPort: string;
  obsPassword: string;
  voicemeeterEnabled: boolean;
  voicemeeterPath: string;
  oscListenPort: number | null;
  overlayEnabled: boolean;
  overlayUseLog: boolean;
  overlayShowNumber: boolean;
  overlayBackgroundColor: string;
  overlayTextColor: string;
  overlayBarColor: string;
  overlayBarBackgroundColor: string;
  overlayWindowCornerRounding: number | null;
  overlayBarHeight: number | null;
  overlayBarCornerRounding: number | null;
  overlayPosition: string | null;
  overlayPadding: number | null;
}

export interface MqttSettings {
  enabled: boolean;
  host: string;
  port: number;
  username: string;
  password: string;
  baseTopic: string;
  homeAssistant: boolean;
}

// ── Process ──────────────────────────────────────────────────────────────────

export interface ProcessDto {
  pid: number;
  path: string;
  name: string;
  icon: string | null;
}

// ── Commands ─────────────────────────────────────────────────────────────────

export type MuteType = 'mute' | 'unmute' | 'toggle';
export type CommandsType = 'allAtOnce' | 'sequential';

export interface DialCommandParams {
  invert: boolean;
  moveStart: number | null;
  moveEnd: number | null;
}

export const DEFAULT_DIAL_PARAMS: DialCommandParams = { invert: false, moveStart: null, moveEnd: null };

const PKG = 'com.getpcpanel.commands.command.';

export interface CmdNoOp           { _type: `${typeof PKG}CommandNoOp` }
export interface CmdMedia          { _type: `${typeof PKG}CommandMedia`;           button: 'mute'|'next'|'prev'|'stop'|'playPause'; spotify: boolean }
export interface CmdKeystroke      { _type: `${typeof PKG}CommandKeystroke`;       keystroke: string }
export interface CmdRun            { _type: `${typeof PKG}CommandRun`;             command: string }
export interface CmdShortcut       { _type: `${typeof PKG}CommandShortcut`;        shortcut: string }
export interface CmdEndProgram     { _type: `${typeof PKG}CommandEndProgram`;      specific: boolean; name: string }
export interface CmdProfile        { _type: `${typeof PKG}CommandProfile`;         profile: string }
export interface CmdBrightness     { _type: `${typeof PKG}CommandBrightness`;      dialParams: DialCommandParams }
export interface CmdVolumeFocus    { _type: `${typeof PKG}CommandVolumeFocus`;     dialParams: DialCommandParams }
export interface CmdVolumeFocusMute{ _type: `${typeof PKG}CommandVolumeFocusMute`; muteType: MuteType }
export interface CmdVolumeDevice   { _type: `${typeof PKG}CommandVolumeDevice`;    deviceId: string; isUnMuteOnVolumeChange: boolean; dialParams: DialCommandParams }
export interface CmdVolumeDeviceMute { _type: `${typeof PKG}CommandVolumeDeviceMute`; deviceId: string; muteType: MuteType }
export interface CmdVolumeDefaultDevice { _type: `${typeof PKG}CommandVolumeDefaultDevice`; deviceId: string }
export interface CmdVolumeDefaultDeviceToggle { _type: `${typeof PKG}CommandVolumeDefaultDeviceToggle`; devices: string[] }
export interface CmdVolumeDefaultDeviceAdvanced { _type: `${typeof PKG}CommandVolumeDefaultDeviceAdvanced`; name: string; mediaPb: string; mediaRec: string; communicationPb: string; communicationRec: string }
export interface CmdVolumeProcess  { _type: `${typeof PKG}CommandVolumeProcess`;   processName: string[]; device: string; isUnMuteOnVolumeChange: boolean; dialParams: DialCommandParams }
export interface CmdVolumeProcessMute { _type: `${typeof PKG}CommandVolumeProcessMute`; processName: string[]; muteType: MuteType }
export interface CmdVolumeAppDeviceToggle { _type: `${typeof PKG}CommandVolumeApplicationDeviceToggle`; processes: string[]; followFocus: boolean; devices: DeviceSet[] }
export interface CmdObsMuteSource  { _type: `${typeof PKG}CommandObsMuteSource`;   source: string; type: MuteType }
export interface CmdObsSetScene    { _type: `${typeof PKG}CommandObsSetScene`;     scene: string }
export interface CmdObsSetSourceVolume { _type: `${typeof PKG}CommandObsSetSourceVolume`; sourceName: string; dialParams: DialCommandParams }
export interface CmdVoiceMeeterBasic { _type: `${typeof PKG}CommandVoiceMeeterBasic`; ct: string; index: number; dt: string; dialParams: DialCommandParams }
export interface CmdVoiceMeeterBasicButton { _type: `${typeof PKG}CommandVoiceMeeterBasicButton`; ct: string; index: number; bt: string }
export interface CmdVoiceMeeterAdvanced { _type: `${typeof PKG}CommandVoiceMeeterAdvanced`; fullParam: string; ct: string; dialParams: DialCommandParams }
export interface CmdVoiceMeeterAdvancedButton { _type: `${typeof PKG}CommandVoiceMeeterAdvancedButton`; fullParam: string; bt: string; stringValue: string | null }

export interface DeviceSet {
  name: string;
  mediaPlayback: string;
  mediaRecord: string;
  communicationPlayback: string;
  communicationRecord: string;
}

export type Command =
  | CmdNoOp | CmdMedia | CmdKeystroke | CmdRun | CmdShortcut | CmdEndProgram | CmdProfile
  | CmdBrightness | CmdVolumeFocus | CmdVolumeFocusMute
  | CmdVolumeDevice | CmdVolumeDeviceMute | CmdVolumeDefaultDevice
  | CmdVolumeDefaultDeviceToggle | CmdVolumeDefaultDeviceAdvanced
  | CmdVolumeProcess | CmdVolumeProcessMute | CmdVolumeAppDeviceToggle
  | CmdObsMuteSource | CmdObsSetScene | CmdObsSetSourceVolume
  | CmdVoiceMeeterBasic | CmdVoiceMeeterBasicButton
  | CmdVoiceMeeterAdvanced | CmdVoiceMeeterAdvancedButton;

export interface CommandsWrapper {
  commands: Command[];
  type: CommandsType;
}

export type CommandKind = 'button' | 'dial';

export interface CommandTypeDef {
  _type: string;
  label: string;
  kinds: CommandKind[];
}

export const COMMAND_TYPE_DEFS: CommandTypeDef[] = [
  { _type: `${PKG}CommandNoOp`,                              label: 'None',                         kinds: ['button','dial'] },
  { _type: `${PKG}CommandMedia`,                             label: 'Media Key',                    kinds: ['button'] },
  { _type: `${PKG}CommandKeystroke`,                         label: 'Keystroke',                    kinds: ['button'] },
  { _type: `${PKG}CommandRun`,                               label: 'Run Command',                  kinds: ['button'] },
  { _type: `${PKG}CommandShortcut`,                          label: 'Shortcut',                     kinds: ['button'] },
  { _type: `${PKG}CommandEndProgram`,                        label: 'End Program',                  kinds: ['button'] },
  { _type: `${PKG}CommandProfile`,                           label: 'Switch Profile',               kinds: ['button'] },
  { _type: `${PKG}CommandVolumeFocus`,                       label: 'Volume (Focused App)',         kinds: ['dial'] },
  { _type: `${PKG}CommandVolumeFocusMute`,                   label: 'Mute (Focused App)',           kinds: ['button'] },
  { _type: `${PKG}CommandVolumeDevice`,                      label: 'Volume (Audio Device)',        kinds: ['dial'] },
  { _type: `${PKG}CommandVolumeDeviceMute`,                  label: 'Mute (Audio Device)',          kinds: ['button'] },
  { _type: `${PKG}CommandVolumeDefaultDevice`,               label: 'Set Default Device',          kinds: ['button'] },
  { _type: `${PKG}CommandVolumeDefaultDeviceToggle`,         label: 'Cycle Default Devices',       kinds: ['button'] },
  { _type: `${PKG}CommandVolumeDefaultDeviceAdvanced`,       label: 'Set Default Device (Advanced)',kinds: ['button'] },
  { _type: `${PKG}CommandVolumeProcess`,                     label: 'Volume (Process)',             kinds: ['dial'] },
  { _type: `${PKG}CommandVolumeProcessMute`,                 label: 'Mute (Process)',               kinds: ['button'] },
  { _type: `${PKG}CommandVolumeApplicationDeviceToggle`,     label: 'App Device Toggle',           kinds: ['button'] },
  { _type: `${PKG}CommandBrightness`,                        label: 'Device Brightness',           kinds: ['dial'] },
  { _type: `${PKG}CommandObsMuteSource`,                     label: 'OBS: Mute Source',            kinds: ['button'] },
  { _type: `${PKG}CommandObsSetScene`,                       label: 'OBS: Set Scene',              kinds: ['button'] },
  { _type: `${PKG}CommandObsSetSourceVolume`,                label: 'OBS: Source Volume',          kinds: ['dial'] },
  { _type: `${PKG}CommandVoiceMeeterBasic`,                  label: 'VoiceMeeter: Dial',           kinds: ['dial'] },
  { _type: `${PKG}CommandVoiceMeeterBasicButton`,            label: 'VoiceMeeter: Button',         kinds: ['button'] },
  { _type: `${PKG}CommandVoiceMeeterAdvanced`,               label: 'VoiceMeeter: Advanced Dial',  kinds: ['dial'] },
  { _type: `${PKG}CommandVoiceMeeterAdvancedButton`,         label: 'VoiceMeeter: Advanced Button',kinds: ['button'] },
];

// ── WebSocket event types ─────────────────────────────────────────────────────

// WebSocket event types
export interface WsEvent {
  type: 'device_connected' | 'device_disconnected' | 'knob_rotate' | 'button_press';
  serial: string;
}

export interface WsKnobEvent extends WsEvent {
  knob: number;
  value: number;
}

export interface WsButtonEvent extends WsEvent {
  button: number;
  pressed: boolean;
}

export interface WaveLinkSettings {
  enabled: boolean;
}
