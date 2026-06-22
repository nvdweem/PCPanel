import { IconName } from '../../ui';

/**
 * Data-driven catalog of every assignable command. One generic editor renders a
 * command's `fields`; `buildEmpty` produces a valid empty instance (shapes copied
 * verbatim from the backend command contract). `kinds` says which control slots a
 * command is valid for: 'dial' = a knob/slider rotate slot (0–100%); 'button' =
 * a knob press / double-press slot.
 */
export type CommandCategory = 'audio' | 'system' | 'integration';
export type CommandKind = 'dial' | 'button';
export type Integration = 'obs' | 'voicemeeter' | 'wavelink' | 'homeassistant';
export type LiveSource =
  | 'obs-scenes' | 'obs-sources' | 'vm-advanced'
  | 'wl-channels' | 'wl-inputs' | 'wl-mixes' | 'wl-outputs' | 'profiles'
  | 'ha-servers';

export type FieldDef =
  | { kind: 'text'; key: string; label: string; placeholder?: string; mono?: boolean }
  | { kind: 'textarea'; key: string; label: string; placeholder?: string; rows?: number }
  | { kind: 'number'; key: string; label: string; min?: number; max?: number }
  | { kind: 'toggle'; key: string; label: string }
  | { kind: 'select'; key: string; label: string; options: { value: string; label: string }[] }
  | { kind: 'select-live'; key: string; label: string; source: LiveSource }
  | { kind: 'apps'; key: string; label: string }
  | { kind: 'device'; key: string; label: string; filter?: 'output' | 'input' | 'all' }
  | { kind: 'mute'; key: string; label: string }
  | { kind: 'keystroke' }                       // CommandKeystroke: KEY/TEXT toggle + combo/text
  | { kind: 'wavelink-target' }                 // id1 (+id2 for Mix), with the source driven by commandType
  | { kind: 'ha-help'; withValue?: boolean }    // links to HA's action builder + server config (+ {{ value }} hint)
  | { kind: 'devices-list'; key: string; label: string };  // cycle list of device ids

export interface CommandDef {
  type: string;
  label: string;
  category: CommandCategory;
  kinds: CommandKind[];
  integration?: Integration;
  icon: IconName;
  buildEmpty: () => Record<string, any>;
  fields: FieldDef[];
}

const P = 'com.getpcpanel.commands.command.';
const WL = 'com.getpcpanel.wavelink.command.';
const HA = 'com.getpcpanel.homeassistant.command.';

const MUTE_OPTS = [
  { value: 'toggle', label: 'Toggle' }, { value: 'mute', label: 'Mute' }, { value: 'unmute', label: 'Unmute' },
];
const dialParams = () => ({ invert: false, moveStart: 0, moveEnd: 0 });

export const COMMANDS: CommandDef[] = [
  // ── AUDIO ────────────────────────────────────────────────────────────────
  {
    type: P + 'CommandVolumeProcess', label: 'App volume', category: 'audio', kinds: ['dial'], icon: 'volume',
    buildEmpty: () => ({ _type: P + 'CommandVolumeProcess', device: '', processName: [], unMuteOnVolumeChange: false, dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'apps', key: 'processName', label: 'Applications' },
      { kind: 'toggle', key: 'unMuteOnVolumeChange', label: 'Unmute on volume change' },
    ],
  },
  {
    type: P + 'CommandVolumeProcessMute', label: 'App mute', category: 'audio', kinds: ['button'], icon: 'volume-x',
    buildEmpty: () => ({ _type: P + 'CommandVolumeProcessMute', muteType: 'toggle', processName: [], overlayText: '' }),
    fields: [
      { kind: 'apps', key: 'processName', label: 'Applications' },
      { kind: 'mute', key: 'muteType', label: 'Action' },
    ],
  },
  {
    type: P + 'CommandVolumeFocus', label: 'Focused-app volume', category: 'audio', kinds: ['dial'], icon: 'volume',
    buildEmpty: () => ({ _type: P + 'CommandVolumeFocus', dialParams: dialParams(), invert: false }),
    fields: [],
  },
  {
    type: P + 'CommandVolumeFocusMute', label: 'Focused-app mute', category: 'audio', kinds: ['button'], icon: 'volume-x',
    buildEmpty: () => ({ _type: P + 'CommandVolumeFocusMute', muteType: 'toggle', overlayText: '' }),
    fields: [{ kind: 'mute', key: 'muteType', label: 'Action' }],
  },
  {
    type: P + 'CommandVolumeDevice', label: 'Device volume', category: 'audio', kinds: ['dial'], icon: 'volume',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDevice', deviceId: '', unMuteOnVolumeChange: false, dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'device', key: 'deviceId', label: 'Audio device', filter: 'all' },
      { kind: 'toggle', key: 'unMuteOnVolumeChange', label: 'Unmute on volume change' },
    ],
  },
  {
    type: P + 'CommandVolumeDeviceMute', label: 'Device mute', category: 'audio', kinds: ['button'], icon: 'volume-x',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDeviceMute', deviceId: '', muteType: 'toggle', overlayText: '' }),
    fields: [
      { kind: 'device', key: 'deviceId', label: 'Audio device', filter: 'all' },
      { kind: 'mute', key: 'muteType', label: 'Action' },
    ],
  },
  {
    type: P + 'CommandVolumeDefaultDevice', label: 'Set default device', category: 'audio', kinds: ['button'], icon: 'monitor',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDefaultDevice', deviceId: '', overlayText: '' }),
    fields: [{ kind: 'device', key: 'deviceId', label: 'Default device', filter: 'all' }],
  },
  {
    type: P + 'CommandVolumeDefaultDeviceToggle', label: 'Cycle default device', category: 'audio', kinds: ['button'], icon: 'refresh',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDefaultDeviceToggle', currentIdx: 0, devices: [], overlayText: '' }),
    fields: [{ kind: 'devices-list', key: 'devices', label: 'Devices to cycle' }],
  },
  {
    type: P + 'CommandVolumeDefaultDeviceAdvanced', label: 'Advanced default device', category: 'audio', kinds: ['button'], icon: 'monitor',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDefaultDeviceAdvanced', communicationPb: '', communicationRec: '', mediaPb: '', mediaRec: '', name: '', overlayText: '' }),
    fields: [
      { kind: 'device', key: 'mediaPb', label: 'Media — playback', filter: 'output' },
      { kind: 'device', key: 'mediaRec', label: 'Media — recording', filter: 'input' },
      { kind: 'device', key: 'communicationPb', label: 'Comms — playback', filter: 'output' },
      { kind: 'device', key: 'communicationRec', label: 'Comms — recording', filter: 'input' },
    ],
  },

  // ── DEVICE & SYSTEM ────────────────────────────────────────────────────────
  {
    type: P + 'CommandBrightness', label: 'Brightness', category: 'system', kinds: ['dial'], icon: 'sun',
    buildEmpty: () => ({ _type: P + 'CommandBrightness', dialParams: dialParams(), invert: false }),
    fields: [],
  },
  {
    type: P + 'CommandProfile', label: 'Switch profile', category: 'system', kinds: ['button'], icon: 'refresh',
    buildEmpty: () => ({ _type: P + 'CommandProfile', profile: '' }),
    fields: [{ kind: 'select-live', key: 'profile', label: 'Profile', source: 'profiles' }],
  },
  {
    type: P + 'CommandRun', label: 'Run command', category: 'system', kinds: ['button'], icon: 'zap',
    buildEmpty: () => ({ _type: P + 'CommandRun', command: '', overlayText: '' }),
    fields: [{ kind: 'text', key: 'command', label: 'Command / program', placeholder: 'e.g. notepad.exe', mono: true }],
  },
  {
    type: P + 'CommandShortcut', label: 'Run shortcut', category: 'system', kinds: ['button'], icon: 'zap',
    buildEmpty: () => ({ _type: P + 'CommandShortcut', shortcut: '', overlayText: '' }),
    fields: [{ kind: 'text', key: 'shortcut', label: 'Shortcut path', placeholder: '…/app.lnk', mono: true }],
  },
  {
    type: P + 'CommandEndProgram', label: 'End program', category: 'system', kinds: ['button'], icon: 'x',
    buildEmpty: () => ({ _type: P + 'CommandEndProgram', name: '', specific: false, overlayText: '' }),
    fields: [
      { kind: 'text', key: 'name', label: 'Process name', placeholder: 'leave blank for focused app' },
      { kind: 'toggle', key: 'specific', label: 'Match this exact process only' },
    ],
  },
  {
    type: P + 'CommandKeystroke', label: 'Keystroke', category: 'system', kinds: ['button'], icon: 'keyboard',
    buildEmpty: () => ({ _type: P + 'CommandKeystroke', type: 'KEY', keystroke: '', text: '', overlayText: '' }),
    fields: [{ kind: 'keystroke' }],
  },
  {
    type: P + 'CommandMedia', label: 'Media', category: 'system', kinds: ['button'], icon: 'play',
    buildEmpty: () => ({ _type: P + 'CommandMedia', button: 'playPause', spotify: false, overlayText: '' }),
    fields: [
      {
        kind: 'select', key: 'button', label: 'Media key', options: [
          { value: 'playPause', label: 'Play / pause' }, { value: 'next', label: 'Next' },
          { value: 'prev', label: 'Previous' }, { value: 'stop', label: 'Stop' }, { value: 'mute', label: 'Mute' },
        ],
      },
      { kind: 'toggle', key: 'spotify', label: 'Spotify-aware' },
    ],
  },

  // ── INTEGRATIONS ───────────────────────────────────────────────────────────
  {
    type: P + 'CommandObsSetSourceVolume', label: 'OBS — source volume', category: 'integration', integration: 'obs', kinds: ['dial'], icon: 'sliders',
    buildEmpty: () => ({ _type: P + 'CommandObsSetSourceVolume', sourceName: '', dialParams: dialParams(), invert: false }),
    fields: [{ kind: 'select-live', key: 'sourceName', label: 'Source', source: 'obs-sources' }],
  },
  {
    type: P + 'CommandObsSetScene', label: 'OBS — switch scene', category: 'integration', integration: 'obs', kinds: ['button'], icon: 'film',
    buildEmpty: () => ({ _type: P + 'CommandObsSetScene', scene: '', overlayText: '' }),
    fields: [{ kind: 'select-live', key: 'scene', label: 'Scene', source: 'obs-scenes' }],
  },
  {
    type: P + 'CommandObsMuteSource', label: 'OBS — mute source', category: 'integration', integration: 'obs', kinds: ['button'], icon: 'mic-off',
    buildEmpty: () => ({ _type: P + 'CommandObsMuteSource', source: '', type: 'toggle', overlayText: '' }),
    fields: [
      { kind: 'select-live', key: 'source', label: 'Source', source: 'obs-sources' },
      { kind: 'mute', key: 'type', label: 'Action' },
    ],
  },
  {
    type: P + 'CommandVoiceMeeterAdvanced', label: 'Voicemeeter — parameter', category: 'integration', integration: 'voicemeeter', kinds: ['dial'], icon: 'sliders',
    buildEmpty: () => ({ _type: P + 'CommandVoiceMeeterAdvanced', ct: 'NEG_12_TO_12', fullParam: '', dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'select-live', key: 'fullParam', label: 'Parameter', source: 'vm-advanced' },
      {
        kind: 'select', key: 'ct', label: 'Range', options: [
          { value: 'NEG_12_TO_12', label: '-12 … +12' }, { value: 'ZERO_TO_10', label: '0 … 10' },
          { value: 'NEG_40_TO_12', label: '-40 … +12' }, { value: 'NEG_60_TO_12', label: '-60 … +12' },
          { value: 'NEG_INF_TO_12', label: '-∞ … +12' }, { value: 'NEG_INF_TO_ZERO', label: '-∞ … 0' },
        ],
      },
    ],
  },
  {
    type: P + 'CommandVoiceMeeterAdvancedButton', label: 'Voicemeeter — button', category: 'integration', integration: 'voicemeeter', kinds: ['button'], icon: 'sliders',
    buildEmpty: () => ({ _type: P + 'CommandVoiceMeeterAdvancedButton', bt: 'TOGGLE', fullParam: '', stringValue: '', overlayText: '' }),
    fields: [
      { kind: 'select-live', key: 'fullParam', label: 'Parameter', source: 'vm-advanced' },
      {
        kind: 'select', key: 'bt', label: 'Action', options: [
          { value: 'TOGGLE', label: 'Toggle' }, { value: 'SET', label: 'Set value' },
        ],
      },
      { kind: 'text', key: 'stringValue', label: 'Value (for Set)', mono: true },
    ],
  },
  {
    type: WL + 'CommandWaveLinkChangeLevel', label: 'Wave Link — level', category: 'integration', integration: 'wavelink', kinds: ['dial'], icon: 'sliders',
    buildEmpty: () => ({ _type: WL + 'CommandWaveLinkChangeLevel', commandType: 'Channel', id1: '', id2: '', dialParams: dialParams(), invert: false }),
    fields: [
      {
        kind: 'select', key: 'commandType', label: 'Target', options: [
          { value: 'Channel', label: 'Channel' }, { value: 'Input', label: 'Input' },
          { value: 'Mix', label: 'Mix' }, { value: 'Output', label: 'Output' },
        ],
      },
      { kind: 'wavelink-target' },
    ],
  },
  {
    type: WL + 'CommandWaveLinkChangeMute', label: 'Wave Link — mute', category: 'integration', integration: 'wavelink', kinds: ['button'], icon: 'mic-off',
    buildEmpty: () => ({ _type: WL + 'CommandWaveLinkChangeMute', commandType: 'Channel', id1: '', id2: '', muteType: 'toggle', overlayText: '' }),
    fields: [
      {
        kind: 'select', key: 'commandType', label: 'Target', options: [
          { value: 'Channel', label: 'Channel' }, { value: 'Mix', label: 'Mix' }, { value: 'Output', label: 'Output' },
        ],
      },
      { kind: 'wavelink-target' },
      { kind: 'mute', key: 'muteType', label: 'Action' },
    ],
  },
  {
    type: WL + 'CommandWaveLinkMainOutput', label: 'Wave Link — main output', category: 'integration', integration: 'wavelink', kinds: ['button'], icon: 'volume',
    buildEmpty: () => ({ _type: WL + 'CommandWaveLinkMainOutput', id: '', name: '', overlayText: '' }),
    fields: [{ kind: 'select-live', key: 'id', label: 'Output', source: 'wl-outputs' }],
  },
  {
    type: WL + 'CommandWaveLinkAddFocusToChannel', label: 'Wave Link — add focused app', category: 'integration', integration: 'wavelink', kinds: ['button'], icon: 'plus',
    buildEmpty: () => ({ _type: WL + 'CommandWaveLinkAddFocusToChannel', id: '', name: '', overlayText: '' }),
    fields: [{ kind: 'select-live', key: 'id', label: 'Channel', source: 'wl-channels' }],
  },
  {
    type: HA + 'CommandHomeAssistantValue', label: 'Home Assistant — set value', category: 'integration', integration: 'homeassistant', kinds: ['dial'], icon: 'sliders',
    buildEmpty: () => ({ _type: HA + 'CommandHomeAssistantValue', server: '', action: '', min: 0, max: 100, formula: '', dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'select-live', key: 'server', label: 'Server', source: 'ha-servers' },
      { kind: 'textarea', key: 'action', label: 'Action (paste YAML from Home Assistant)', rows: 6, placeholder: 'action: light.turn_on\ntarget:\n  entity_id: light.living_room\ndata:\n  brightness: {{ value }}' },
      { kind: 'ha-help', withValue: true },
      { kind: 'number', key: 'min', label: 'Value at 0% (no formula)' },
      { kind: 'number', key: 'max', label: 'Value at 100% (no formula)' },
      { kind: 'text', key: 'formula', label: 'Translate formula (optional)', placeholder: 'x is 0..1 — e.g. x*255 or 2000+x*4000', mono: true },
    ],
  },
  {
    type: HA + 'CommandHomeAssistantAction', label: 'Home Assistant — perform action', category: 'integration', integration: 'homeassistant', kinds: ['button'], icon: 'zap',
    buildEmpty: () => ({ _type: HA + 'CommandHomeAssistantAction', server: '', action: '', overlayText: '' }),
    fields: [
      { kind: 'select-live', key: 'server', label: 'Server', source: 'ha-servers' },
      { kind: 'textarea', key: 'action', label: 'Action (paste YAML from Home Assistant)', rows: 6, placeholder: 'action: light.toggle\ntarget:\n  entity_id: light.living_room' },
      { kind: 'ha-help' },
    ],
  },
  {
    type: P + 'CommandObsAction', label: 'OBS — stream / record', category: 'integration', integration: 'obs', kinds: ['button'], icon: 'film',
    buildEmpty: () => ({ _type: P + 'CommandObsAction', action: 'TOGGLE_STREAM', overlayText: '' }),
    fields: [
      {
        kind: 'select', key: 'action', label: 'Action', options: [
          { value: 'START_STREAM', label: 'Start streaming' }, { value: 'STOP_STREAM', label: 'Stop streaming' }, { value: 'TOGGLE_STREAM', label: 'Toggle streaming' },
          { value: 'START_RECORD', label: 'Start recording' }, { value: 'STOP_RECORD', label: 'Stop recording' }, { value: 'TOGGLE_RECORD', label: 'Toggle recording' },
          { value: 'TOGGLE_RECORD_PAUSE', label: 'Pause / resume recording' },
          { value: 'START_VIRTUAL_CAM', label: 'Start virtual camera' }, { value: 'STOP_VIRTUAL_CAM', label: 'Stop virtual camera' }, { value: 'TOGGLE_VIRTUAL_CAM', label: 'Toggle virtual camera' },
          { value: 'TOGGLE_REPLAY_BUFFER', label: 'Toggle replay buffer' }, { value: 'SAVE_REPLAY_BUFFER', label: 'Save replay buffer' },
        ],
      },
    ],
  },

  // ── GENERIC OUTPUTS (HTTP / MQTT / OSC) ─────────────────────────────────────
  // On a dial the position maps (min/max or formula) to the number that replaces {{ value }};
  // on a button the value is resolved at full scale (the configured max).
  {
    type: P + 'CommandHttpRequest', label: 'HTTP request', category: 'integration', kinds: ['dial', 'button'], icon: 'zap',
    buildEmpty: () => ({ _type: P + 'CommandHttpRequest', url: '', method: 'GET', headers: '', body: '', min: 0, max: 100, formula: '', dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'text', key: 'url', label: 'URL', placeholder: 'https://host/path?v={{ value }}', mono: true },
      {
        kind: 'select', key: 'method', label: 'Method', options: [
          { value: 'GET', label: 'GET' }, { value: 'POST', label: 'POST' }, { value: 'PUT', label: 'PUT' },
          { value: 'PATCH', label: 'PATCH' }, { value: 'DELETE', label: 'DELETE' },
        ],
      },
      { kind: 'textarea', key: 'headers', label: 'Headers (one Name: Value per line)', rows: 3, placeholder: 'Content-Type: application/json\nAuthorization: Bearer …' },
      { kind: 'textarea', key: 'body', label: 'Body', rows: 4, placeholder: '{ "value": {{ value }} }' },
      { kind: 'number', key: 'min', label: 'Value at 0% (no formula)' },
      { kind: 'number', key: 'max', label: 'Value at 100% (no formula)' },
      { kind: 'text', key: 'formula', label: 'Translate formula (optional)', placeholder: 'x is 0..1 — e.g. x*255 or 2000+x*4000', mono: true },
    ],
  },
  {
    type: P + 'CommandMqttPublish', label: 'MQTT publish', category: 'integration', kinds: ['dial', 'button'], icon: 'zap',
    buildEmpty: () => ({ _type: P + 'CommandMqttPublish', topic: '', payload: '', min: 0, max: 100, formula: '', dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'text', key: 'topic', label: 'Topic', placeholder: 'home/livingroom/light', mono: true },
      { kind: 'textarea', key: 'payload', label: 'Payload', rows: 3, placeholder: '{{ value }} or e.g. {"brightness": {{ value }}}' },
      { kind: 'number', key: 'min', label: 'Value at 0% (no formula)' },
      { kind: 'number', key: 'max', label: 'Value at 100% (no formula)' },
      { kind: 'text', key: 'formula', label: 'Translate formula (optional)', placeholder: 'x is 0..1 — e.g. x*255', mono: true },
    ],
  },
  {
    type: P + 'CommandOscSend', label: 'OSC send', category: 'integration', kinds: ['dial', 'button'], icon: 'sliders',
    buildEmpty: () => ({ _type: P + 'CommandOscSend', address: '', min: 0, max: 100, formula: '', dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'text', key: 'address', label: 'OSC address', placeholder: '/track/1/volume', mono: true },
      { kind: 'number', key: 'min', label: 'Value at 0% (no formula)' },
      { kind: 'number', key: 'max', label: 'Value at 100% (no formula)' },
      { kind: 'text', key: 'formula', label: 'Translate formula (optional)', placeholder: 'x is 0..1 — e.g. x or x*127', mono: true },
    ],
  },
];

export const COMMAND_BY_TYPE = new Map(COMMANDS.map(c => [c.type, c]));

export function commandsForKind(kind: CommandKind): CommandDef[] {
  return COMMANDS.filter(c => c.kinds.includes(kind));
}

export function categoryLabel(cat: CommandCategory): string {
  return cat === 'audio' ? 'AUDIO' : cat === 'system' ? 'DEVICE & SYSTEM' : 'INTEGRATIONS';
}
