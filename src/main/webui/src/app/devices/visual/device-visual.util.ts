import { Command, Commands, LightingConfig } from '../../models/generated/backend.types';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import {
  ColorVisual, knobRingColor, lightingAnimClass, lightingAnimDuration, lightingBreathMin, resolveColorVisual,
} from '../pcpanel/lighting-animation';

/** Percent (0–100) for an analog value within its source range.
 *  Defaults to the canonical 0–255 domain (PCPanel Mini/Pro), which is what the
 *  WS snapshot already normalizes to; pass a control's sourceMin/sourceMax to
 *  render a device with a different raw range correctly. */
export function analogPct(value: number | undefined, min = 0, max = 255): number {
  const span = max - min;
  if (span <= 0) return 0;
  return Math.max(0, Math.min(100, (((value ?? 0) - min) / span) * 100));
}

/** Resolve a backend color (possibly a $RAINBOW!/$BREATH token) + the device's
 *  global animation context into an SVG/CSS-ready {fill, animClass, ...}. */
export function controlVisual(color: string | undefined, config: LightingConfig | null | undefined, fallback: string): ColorVisual {
  if (!config) return { fill: color || fallback, animClass: '', animDuration: '0s', breathMin: 0.18 };
  return resolveColorVisual(
    color || fallback,
    lightingAnimClass(config),
    lightingAnimDuration(config),
    lightingBreathMin(config),
  );
}

/** Knob ring color falling back to the config-derived color when the snapshot
 *  has no precomputed dialColors (older Mini/RGB snapshots). */
export function knobColor(dialColors: string[] | undefined, i: number, total: number, config: LightingConfig | null | undefined, fallback: string): string {
  const c = dialColors?.[i];
  if (c) return c;
  return knobRingColor(config, i, total, fallback);
}

const SHORT: Record<string, (c: Command) => string | undefined> = {
  CommandVolumeProcess: c => firstName((c as any).processName),
  CommandVolumeProcessMute: c => mutePrefix(c) + (firstName((c as any).processName) ?? 'app'),
  CommandVolumeDevice: () => 'Device vol',
  CommandVolumeDeviceMute: c => mutePrefix(c) + 'device',
  CommandVolumeFocus: () => 'Focus vol',
  CommandVolumeFocusMute: c => mutePrefix(c) + 'focus',
  CommandBrightness: () => 'Brightness',
  CommandProfile: () => 'Switch profile',
  CommandMedia: c => mediaLabel((c as any).button),
  CommandKeystroke: () => 'Keystroke',
  CommandShortcut: () => 'Shortcut',
  CommandRun: () => 'Run',
  CommandEndProgram: () => 'End program',
  CommandObsSetScene: () => 'OBS scene',
  CommandObsMuteSource: () => 'OBS mute',
  CommandObsSetSourceVolume: () => 'OBS vol',
  CommandObsAction: () => 'OBS',
  CommandHttpRequest: c => httpLabel(c),
  CommandMqttPublish: () => 'MQTT',
  CommandOscSend: () => 'OSC',
  CommandVolumeDefaultDevice: () => 'Default device',
  CommandVolumeDefaultDeviceToggle: () => 'Cycle device',
};

function firstName(arr: unknown): string | undefined {
  return Array.isArray(arr) && arr.length ? String(arr[0]) : undefined;
}
function mutePrefix(c: Command): string {
  return mutePrefixOf((c as any).muteType);
}
function mutePrefixOf(t: string | undefined): string {
  return t === 'unmute' ? 'Unmute ' : 'Mute ';
}
function mediaLabel(b: string): string {
  return ({ mute: 'Mute', next: 'Next', prev: 'Prev', stop: 'Stop', playPause: 'Play/Pause' } as Record<string, string>)[b] ?? 'Media';
}
function httpLabel(c: Command): string {
  const m = (c as any).method;
  return m ? `HTTP ${String(m).toUpperCase()}` : 'HTTP';
}
function obsActionLabel(action: string): string {
  return ({
    START_STREAM: 'Start stream', STOP_STREAM: 'Stop stream', TOGGLE_STREAM: 'Toggle stream',
    START_RECORD: 'Start record', STOP_RECORD: 'Stop record', TOGGLE_RECORD: 'Toggle record',
    TOGGLE_RECORD_PAUSE: 'Pause record', START_VIRTUAL_CAM: 'Start virtual cam', STOP_VIRTUAL_CAM: 'Stop virtual cam',
    TOGGLE_VIRTUAL_CAM: 'Toggle virtual cam', TOGGLE_REPLAY_BUFFER: 'Toggle replay', SAVE_REPLAY_BUFFER: 'Save replay',
  } as Record<string, string>)[action] ?? 'Action';
}

function typeNameOf(cmd: Command): string {
  return (cmd._type?.split('.').pop() ?? '').replace(/^Command/, '');
}
function wlNameOf(data: IntegrationDataService, id: string | undefined): string | undefined {
  if (!id) return undefined;
  const all = [...data.wlChannels(), ...data.wlInputs(), ...data.wlMixes(), ...data.wlOutputs()];
  return all.find(x => x.id === id)?.name || undefined;
}
function deviceNameOf(data: IntegrationDataService, id: string | undefined): string | undefined {
  if (!id) return undefined;
  return (data.audioDevices.value() ?? []).find(d => d.id === id)?.name || undefined;
}

/**
 * Descriptive label for a configured command that names its actual target — e.g. the Wave Link
 * channel, OBS source, audio device or app it acts on ("Music — Wave Link", "Mic — OBS"). Names
 * that are only known to the backend (Wave Link ids, audio-device ids) are resolved through the
 * live {@link IntegrationDataService} lists. Returns '' for commands that have no meaningful target
 * to name, so callers can fall back to a generic label.
 */
export function describeCommand(cmd: Command | undefined, data: IntegrationDataService): string {
  if (!cmd) return '';
  const c = cmd as any;
  switch (typeNameOf(cmd)) {
    case 'VolumeProcess': { const n = firstName(c.processName); return n ? `${n} — Volume` : ''; }
    case 'VolumeProcessMute': { const n = firstName(c.processName); return n ? `${mutePrefix(cmd)}${n}` : ''; }
    case 'VolumeDevice': { const n = deviceNameOf(data, c.deviceId); return n ? `${n} — Volume` : ''; }
    case 'VolumeDeviceMute': { const n = deviceNameOf(data, c.deviceId); return n ? `${mutePrefix(cmd)}${n}` : ''; }
    case 'VolumeDefaultDevice': { const n = deviceNameOf(data, c.deviceId); return n ? `${n} — Default device` : ''; }
    case 'ObsSetSourceVolume': return c.sourceName ? `${c.sourceName} — OBS` : '';
    case 'ObsSetScene': return c.scene ? `${c.scene} — OBS` : '';
    case 'ObsMuteSource': return c.source ? `${mutePrefixOf(c.type)}${c.source} — OBS` : '';
    case 'ObsAction': return c.action ? `${obsActionLabel(c.action)} — OBS` : '';
    case 'HttpRequest': return c.url ? `${httpLabel(cmd)} ${c.url}` : '';
    case 'MqttPublish': return c.topic ? `${c.topic} — MQTT` : '';
    case 'OscSend': return c.address ? `${c.address} — OSC` : '';
    case 'VoiceMeeterAdvanced':
    case 'VoiceMeeterAdvancedButton': return c.fullParam ? `${c.fullParam} — Voicemeeter` : '';
    case 'WaveLinkChangeLevel': { const n = wlNameOf(data, c.id1); return n ? `${n} — Wave Link` : ''; }
    case 'WaveLinkChangeMute': { const n = wlNameOf(data, c.id1); return n ? `${mutePrefix(cmd)}${n} — Wave Link` : ''; }
    case 'WaveLinkMainOutput': { const n = wlNameOf(data, c.id) || c.name; return n ? `${n} — Wave Link` : ''; }
    case 'WaveLinkAddFocusToChannel': { const n = wlNameOf(data, c.id) || c.name; return n ? `Add focus → ${n} — Wave Link` : ''; }
    default: return '';
  }
}

/** The process name a control's first command targets (for app-icon lookup), if any. */
export function processNameOf(cmds: Commands | null | undefined): string | undefined {
  const pn = (cmds?.commands?.[0] as any)?.processName;
  return Array.isArray(pn) && pn.length ? String(pn[0]) : undefined;
}

/** Short human label for a control's command list (for chips / labels). When the live integration
 *  data is supplied, the named-target form ("Music — Wave Link") is preferred. */
export function shortLabel(cmds: Commands | null | undefined, data?: IntegrationDataService): string {
  const first = cmds?.commands?.[0];
  if (!first) return '';
  if (data) {
    const described = describeCommand(first, data);
    if (described) return described;
  }
  const typeName = (first._type?.split('.').pop() ?? '').replace(/^Command/, '');
  const fn = SHORT[`Command${typeName}`];
  const label = fn?.(first);
  if (label) return label;
  // fall back to spaced type name
  return typeName.replace(/([a-z])([A-Z])/g, '$1 $2');
}

export type { ColorVisual };
