import { IconName } from '../../ui';
import { GENERATED_COMMANDS } from './command-registry.generated';

/**
 * Data-driven catalog of every assignable command. One generic editor renders a
 * command's `fields`; `buildEmpty` produces a valid empty instance (shapes copied
 * verbatim from the backend command contract). `kinds` says which control slots a
 * command is valid for: 'dial' = a knob/slider rotate slot (0–100%); 'button' =
 * a knob press / double-press slot.
 */
export type CommandCategory = 'audio' | 'system' | 'integration';
export type CommandKind = 'dial' | 'button';
export type Integration = 'obs' | 'voicemeeter' | 'wavelink' | 'discord' | 'homeassistant';
export type LiveSource =
  | 'obs-scenes' | 'obs-sources' | 'vm-advanced'
  | 'wl-channels' | 'wl-inputs' | 'wl-mixes' | 'wl-outputs' | 'profiles'
  | 'discord-users' | 'discord-channels' | 'discord-mute-targets' | 'discord-volume-targets'
  | 'ha-servers';

// `showWhen` hides a field unless another field's current value matches — e.g. a process picker shown
// only for the "specific app" mode. Intersected onto every variant so it stays optional everywhere.
export type FieldDef = (
  | { kind: 'text'; key: string; label: string; placeholder?: string; mono?: boolean }
  | { kind: 'textarea'; key: string; label: string; placeholder?: string; rows?: number }
  | { kind: 'number'; key: string; label: string; min?: number; max?: number }
  | { kind: 'toggle'; key: string; label: string }
  | { kind: 'select'; key: string; label: string; options: { value: string; label: string }[] }
  | { kind: 'select-live'; key: string; label: string; source: LiveSource; searchable?: boolean }
  | { kind: 'apps'; key: string; label: string; single?: boolean }  // single: pick exactly one app (still stored as a 1-element array)
  | { kind: 'device'; key: string; label: string; filter?: 'output' | 'input' | 'all'; defaultLabel?: string }  // defaultLabel: adds a selectable empty option (e.g. "Default device") for commands where blank = the default
  | { kind: 'mute'; key: string; label: string }
  | { kind: 'keystroke' }                       // CommandKeystroke: KEY/TEXT toggle + combo/text
  | { kind: 'wavelink-target' }                 // id1 (+id2 for Mix), with the source driven by commandType
  | { kind: 'ha-help'; withValue?: boolean }    // links to HA's action builder + server config (+ {{ value }} hint)
  | { kind: 'devices-list'; key: string; label: string }  // cycle list of device ids
  | { kind: 'analog-bands' }                    // CommandAnalogBands: ordered ranges, each with a colour + nested action
) & { showWhen?: { key: string; equals: string } };

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
const DC = 'com.getpcpanel.discord.command.';
const HA = 'com.getpcpanel.homeassistant.command.';

const MUTE_OPTS = [
  { value: 'toggle', label: 'Toggle' }, { value: 'mute', label: 'Mute' }, { value: 'unmute', label: 'Unmute' },
];
const dialParams = () => ({ invert: false, moveStart: 0, moveEnd: 0 });

interface FieldDef_ { type: string; buildEmpty: () => Record<string, any>; fields: FieldDef[]; }
const FIELD_DEFS: FieldDef_[] = [
  // ── AUDIO ────────────────────────────────────────────────────────────────
  {
    type: P + 'CommandVolumeProcess',
    buildEmpty: () => ({ _type: P + 'CommandVolumeProcess', device: '', processName: [], unMuteOnVolumeChange: false, dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'apps', key: 'processName', label: 'Applications' },
      { kind: 'toggle', key: 'unMuteOnVolumeChange', label: 'Unmute on volume change' },
    ],
  },
  {
    type: P + 'CommandVolumeProcessMute',
    buildEmpty: () => ({ _type: P + 'CommandVolumeProcessMute', muteType: 'toggle', processName: [], overlayText: '' }),
    fields: [
      { kind: 'apps', key: 'processName', label: 'Applications' },
      { kind: 'mute', key: 'muteType', label: 'Action' },
    ],
  },
  {
    type: P + 'CommandVolumeFocus',
    buildEmpty: () => ({ _type: P + 'CommandVolumeFocus', dialParams: dialParams(), invert: false }),
    fields: [],
  },
  {
    type: P + 'CommandVolumeFocusMute',
    buildEmpty: () => ({ _type: P + 'CommandVolumeFocusMute', muteType: 'toggle', overlayText: '' }),
    fields: [{ kind: 'mute', key: 'muteType', label: 'Action' }],
  },
  {
    type: P + 'CommandVolumeDevice',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDevice', deviceId: '', unMuteOnVolumeChange: false, dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'device', key: 'deviceId', label: 'Audio device', filter: 'all', defaultLabel: 'Default device' },
      { kind: 'toggle', key: 'unMuteOnVolumeChange', label: 'Unmute on volume change' },
    ],
  },
  {
    type: P + 'CommandVolumeDeviceMute',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDeviceMute', deviceId: '', muteType: 'toggle', overlayText: '' }),
    fields: [
      { kind: 'device', key: 'deviceId', label: 'Audio device', filter: 'all', defaultLabel: 'Default device' },
      { kind: 'mute', key: 'muteType', label: 'Action' },
    ],
  },
  {
    type: P + 'CommandVolumeDefaultDevice',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDefaultDevice', deviceId: '', overlayText: '' }),
    fields: [{ kind: 'device', key: 'deviceId', label: 'Default device', filter: 'all' }],
  },
  {
    type: P + 'CommandVolumeDefaultDeviceToggle',
    buildEmpty: () => ({ _type: P + 'CommandVolumeDefaultDeviceToggle', currentIdx: 0, devices: [], overlayText: '' }),
    fields: [{ kind: 'devices-list', key: 'devices', label: 'Devices to cycle' }],
  },
  {
    type: P + 'CommandVolumeDefaultDeviceAdvanced',
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
    type: P + 'CommandBrightness',
    buildEmpty: () => ({ _type: P + 'CommandBrightness', dialParams: dialParams(), invert: false }),
    fields: [],
  },
  {
    type: P + 'CommandProfile',
    buildEmpty: () => ({ _type: P + 'CommandProfile', profile: '' }),
    fields: [{ kind: 'select-live', key: 'profile', label: 'Profile', source: 'profiles' }],
  },
  {
    type: P + 'CommandAnalogBands',
    buildEmpty: () => ({ _type: P + 'CommandAnalogBands', bands: [] }),
    fields: [{ kind: 'analog-bands' }],
  },
  {
    type: P + 'CommandRun',
    buildEmpty: () => ({ _type: P + 'CommandRun', command: '', overlayText: '' }),
    fields: [{ kind: 'text', key: 'command', label: 'Command / program', placeholder: 'e.g. notepad.exe', mono: true }],
  },
  {
    type: P + 'CommandShortcut',
    buildEmpty: () => ({ _type: P + 'CommandShortcut', shortcut: '', overlayText: '' }),
    fields: [{ kind: 'text', key: 'shortcut', label: 'Shortcut path', placeholder: '…/app.lnk', mono: true }],
  },
  {
    type: P + 'CommandEndProgram',
    buildEmpty: () => ({ _type: P + 'CommandEndProgram', name: '', specific: false, overlayText: '' }),
    fields: [
      { kind: 'text', key: 'name', label: 'Process name', placeholder: 'leave blank for focused app' },
      { kind: 'toggle', key: 'specific', label: 'Match this exact process only' },
    ],
  },
  {
    type: P + 'CommandKeystroke',
    buildEmpty: () => ({ _type: P + 'CommandKeystroke', type: 'KEY', keystroke: '', text: '', overlayText: '' }),
    fields: [{ kind: 'keystroke' }],
  },
  {
    type: P + 'CommandMedia',
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
  // Generic outputs: send to anything over HTTP/MQTT/OSC. On a dial the position maps (min/max or
  // formula) to the number replacing {{ value }}; on a button the value resolves at full scale.
  {
    type: P + 'CommandHttpRequest',
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
    type: P + 'CommandMqttPublish',
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
    type: P + 'CommandOscSend',
    buildEmpty: () => ({ _type: P + 'CommandOscSend', address: '', min: 0, max: 100, formula: '', dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'text', key: 'address', label: 'OSC address', placeholder: '/track/1/volume', mono: true },
      { kind: 'number', key: 'min', label: 'Value at 0% (no formula)' },
      { kind: 'number', key: 'max', label: 'Value at 100% (no formula)' },
      { kind: 'text', key: 'formula', label: 'Translate formula (optional)', placeholder: 'x is 0..1 — e.g. x or x*127', mono: true },
    ],
  },

  // ── INTEGRATIONS ───────────────────────────────────────────────────────────
  {
    type: P + 'CommandObsSetSourceVolume',
    buildEmpty: () => ({ _type: P + 'CommandObsSetSourceVolume', sourceName: '', dialParams: dialParams(), invert: false }),
    fields: [{ kind: 'select-live', key: 'sourceName', label: 'Source', source: 'obs-sources' }],
  },
  {
    type: P + 'CommandObsSetScene',
    buildEmpty: () => ({ _type: P + 'CommandObsSetScene', scene: '', overlayText: '' }),
    fields: [{ kind: 'select-live', key: 'scene', label: 'Scene', source: 'obs-scenes' }],
  },
  {
    type: P + 'CommandObsMuteSource',
    buildEmpty: () => ({ _type: P + 'CommandObsMuteSource', source: '', type: 'toggle', overlayText: '' }),
    fields: [
      { kind: 'select-live', key: 'source', label: 'Source', source: 'obs-sources' },
      { kind: 'mute', key: 'type', label: 'Action' },
    ],
  },
  {
    type: P + 'CommandObsAction',
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
  {
    type: P + 'CommandVoiceMeeterAdvanced',
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
    type: P + 'CommandVoiceMeeterAdvancedButton',
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
    type: WL + 'CommandWaveLinkChangeLevel',
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
    type: WL + 'CommandWaveLinkChangeMute',
    buildEmpty: () => ({ _type: WL + 'CommandWaveLinkChangeMute', commandType: 'Channel', id1: '', id2: '', muteType: 'toggle', overlayText: '' }),
    fields: [
      {
        kind: 'select', key: 'commandType', label: 'Target', options: [
          { value: 'Channel', label: 'Channel' }, { value: 'Input', label: 'Input' },
          { value: 'Mix', label: 'Mix' }, { value: 'Output', label: 'Output' },
        ],
      },
      { kind: 'wavelink-target' },
      { kind: 'mute', key: 'muteType', label: 'Action' },
    ],
  },
  {
    type: WL + 'CommandWaveLinkMainOutput',
    buildEmpty: () => ({ _type: WL + 'CommandWaveLinkMainOutput', id: '', name: '', overlayText: '' }),
    fields: [{ kind: 'select-live', key: 'id', label: 'Output', source: 'wl-outputs' }],
  },
  {
    type: WL + 'CommandWaveLinkAddFocusToChannel',
    buildEmpty: () => ({ _type: WL + 'CommandWaveLinkAddFocusToChannel', id: '', name: '', overlayText: '' }),
    fields: [{ kind: 'select-live', key: 'id', label: 'Channel', source: 'wl-channels' }],
  },
  {
    type: DC + 'CommandDiscordMute',
    buildEmpty: () => ({ _type: DC + 'CommandDiscordMute', target: 'self', muteType: 'toggle', overlayText: '' }),
    fields: [
      { kind: 'select-live', key: 'target', label: 'Target', source: 'discord-mute-targets', searchable: true },
      { kind: 'mute', key: 'muteType', label: 'Action' },
    ],
  },
  {
    type: DC + 'CommandDiscordSelfDeafen',
    buildEmpty: () => ({ _type: DC + 'CommandDiscordSelfDeafen', muteType: 'toggle', overlayText: '' }),
    fields: [{ kind: 'mute', key: 'muteType', label: 'Action' }],
  },
  {
    type: DC + 'CommandDiscordVolume',
    buildEmpty: () => ({ _type: DC + 'CommandDiscordVolume', target: 'mic', clearMuteOnChange: false, dialParams: dialParams(), invert: false }),
    fields: [
      { kind: 'select-live', key: 'target', label: 'Target', source: 'discord-volume-targets', searchable: true },
      { kind: 'toggle', key: 'clearMuteOnChange', label: 'Unmute/undeafen when changed' },
    ],
  },
  {
    type: DC + 'CommandDiscordScreenShare',
    buildEmpty: () => ({ _type: DC + 'CommandDiscordScreenShare', mode: 'SCREEN', processName: [], overlayText: '' }),
    fields: [
      {
        kind: 'select', key: 'mode', label: 'Share', options: [
          { value: 'SCREEN', label: 'Choose in Discord' }, { value: 'PROCESS', label: 'Specific app' }, { value: 'FOCUS', label: 'Focused app' },
        ],
      },
      { kind: 'apps', key: 'processName', label: 'App', single: true, showWhen: { key: 'mode', equals: 'PROCESS' } },
    ],
  },
  {
    type: DC + 'CommandDiscordToggleVideo',
    buildEmpty: () => ({ _type: DC + 'CommandDiscordToggleVideo', overlayText: '' }),
    fields: [],
  },
  {
    type: DC + 'CommandDiscordJoinVoice',
    buildEmpty: () => ({ _type: DC + 'CommandDiscordJoinVoice', channelId: '', channelName: '', overlayText: '' }),
    fields: [{ kind: 'select-live', key: 'channelId', label: 'Voice channel', source: 'discord-channels' }],
  },
  {
    type: DC + 'CommandDiscordLeaveVoice',
    buildEmpty: () => ({ _type: DC + 'CommandDiscordLeaveVoice', overlayText: '' }),
    fields: [],
  },
  {
    type: HA + 'CommandHomeAssistantValue',
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
    type: HA + 'CommandHomeAssistantAction',
    buildEmpty: () => ({ _type: HA + 'CommandHomeAssistantAction', server: '', action: '', overlayText: '' }),
    fields: [
      { kind: 'select-live', key: 'server', label: 'Server', source: 'ha-servers' },
      { kind: 'textarea', key: 'action', label: 'Action (paste YAML from Home Assistant)', rows: 6, placeholder: 'action: light.toggle\ntarget:\n  entity_id: light.living_room' },
      { kind: 'ha-help' },
    ],
  },
];

// COMMANDS is assembled from the Java-generated registry (label/category/kinds/integration/icon)
// joined with the hand-written field editors above, keyed by the command's persisted type id.
const FIELDS_BY_TYPE = new Map(FIELD_DEFS.map(d => [d.type, d] as const));
export const COMMANDS: CommandDef[] = GENERATED_COMMANDS.map(g => {
  const f = FIELDS_BY_TYPE.get(g.type);
  if (!f) throw new Error('No field schema for command ' + g.type);
  return { ...g, buildEmpty: f.buildEmpty, fields: f.fields };
});

export const COMMAND_BY_TYPE = new Map(COMMANDS.map(c => [c.type, c]));

export function commandsForKind(kind: CommandKind): CommandDef[] {
  return COMMANDS.filter(c => c.kinds.includes(kind));
}

export function categoryLabel(cat: CommandCategory): string {
  return cat === 'audio' ? 'AUDIO' : cat === 'system' ? 'DEVICE & SYSTEM' : 'INTEGRATIONS';
}
