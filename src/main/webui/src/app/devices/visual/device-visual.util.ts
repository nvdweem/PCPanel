import { Command, Commands, LightingConfig } from '../../models/generated/backend.types';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { COMMAND_BY_TYPE } from '../../features/commands/command-catalog';
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

function firstName(arr: unknown): string | undefined {
  return Array.isArray(arr) && arr.length ? String(arr[0]) : undefined;
}
function mutePrefix(c: Command): string {
  return mutePrefixOf((c as any).muteType);
}
function mutePrefixOf(t: string | undefined): string {
  return t === 'unmute' ? 'Unmute ' : 'Mute ';
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

function wlNameOf(data: IntegrationDataService, id: string | undefined): string | undefined {
  if (!id) return undefined;
  const all = [...data.wlChannels(), ...data.wlInputs(), ...data.wlMixes(), ...data.wlOutputs()];
  return all.find(x => x.id === id)?.name || undefined;
}
function deviceNameOf(data: IntegrationDataService, id: string | undefined): string | undefined {
  if (!id) return undefined;
  return (data.audioDevices.value() ?? []).find(d => d.id === id)?.name || undefined;
}
/** Friendly display name for a Discord user targeted by username; the raw username when the live list
 *  isn't loaded (Discord off), so a configured target still names itself. */
function discordNameOf(data: IntegrationDataService, username: string | undefined): string | undefined {
  if (!username) return undefined;
  return (data.discordUsers.value() ?? []).find(u => u.username === username)?.displayName || username;
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
  // Keyed on the persisted _type id (the short "integration.name" form emitted by @CommandMeta), so it
  // survives the class-name → id rename. Returns '' when there's no target to name; shortLabel then uses
  // the command's catalog label.
  switch (c._type as string) {
    case 'volume.process': { const n = firstName(c.processName); return n ? `${n} — Volume` : ''; }
    case 'volume.process-mute': { const n = firstName(c.processName); return n ? `${mutePrefix(cmd)}${n}` : ''; }
    case 'volume.device': { const n = deviceNameOf(data, c.deviceId); return n ? `${n} — Volume` : ''; }
    case 'volume.device-mute': { const n = deviceNameOf(data, c.deviceId); return n ? `${mutePrefix(cmd)}${n}` : ''; }
    case 'volume.default-device': { const n = deviceNameOf(data, c.deviceId); return n ? `${n} — Default device` : ''; }
    case 'obs.set-source-volume': return c.sourceName ? `${c.sourceName} — OBS` : '';
    case 'obs.set-scene': return c.scene ? `${c.scene} — OBS` : '';
    case 'obs.mute-source': return c.source ? `${mutePrefixOf(c.type)}${c.source} — OBS` : '';
    case 'obs.action': return c.action ? `${obsActionLabel(c.action)} — OBS` : '';
    case 'output.http-request': return c.url ? `${httpLabel(cmd)} ${c.url}` : '';
    case 'mqtt.publish': return c.topic ? `${c.topic} — MQTT` : '';
    case 'osc.send': return c.address ? `${c.address} — OSC` : '';
    case 'voicemeeter.advanced':
    case 'voicemeeter.advanced-button': return c.fullParam ? `${c.fullParam} — Voicemeeter` : '';
    case 'wavelink.change-level': { const n = wlNameOf(data, c.id1); return n ? `${n} — Wave Link` : ''; }
    case 'wavelink.change-mute': { const n = wlNameOf(data, c.id1); return n ? `${mutePrefix(cmd)}${n} — Wave Link` : ''; }
    case 'wavelink.main-output': { const n = wlNameOf(data, c.id) || c.name; return n ? `${n} — Wave Link` : ''; }
    case 'wavelink.add-focus-to-channel': { const n = wlNameOf(data, c.id) || c.name; return n ? `Add focus → ${n} — Wave Link` : ''; }
    case 'discord.volume': {
      // mic/output are your own; anything else is another member's username (raw when Discord is offline).
      if (!c.target || c.target === 'mic') return 'Mic — Discord';
      if (c.target === 'output') return 'Output — Discord';
      return `${discordNameOf(data, c.target)} — Discord`;
    }
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
  // No named target — use the command's catalog label ("App volume", "Brightness", "Discord — volume").
  const meta = COMMAND_BY_TYPE.get((first as any)._type);
  if (meta) return meta.label;
  // Last resort for an unknown id: its final path segment.
  return (first._type?.split('.').pop() ?? '');
}

export type { ColorVisual };
