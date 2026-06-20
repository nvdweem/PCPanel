import { Command, Commands, LightingConfig } from '../../models/generated/backend.types';
import {
  ColorVisual, knobRingColor, lightingAnimClass, lightingAnimDuration, lightingBreathMin, resolveColorVisual,
} from '../pcpanel/lighting-animation';

/** Percent (0–100) for an analog value (0–255). */
export function analogPct(value: number | undefined): number {
  return Math.max(0, Math.min(100, ((value ?? 0) / 255) * 100));
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
  CommandVolumeDefaultDevice: () => 'Default device',
  CommandVolumeDefaultDeviceToggle: () => 'Cycle device',
};

function firstName(arr: unknown): string | undefined {
  return Array.isArray(arr) && arr.length ? String(arr[0]) : undefined;
}
function mutePrefix(c: Command): string {
  const t = (c as any).muteType;
  return t === 'unmute' ? 'Unmute ' : t === 'mute' ? 'Mute ' : 'Mute ';
}
function mediaLabel(b: string): string {
  return ({ mute: 'Mute', next: 'Next', prev: 'Prev', stop: 'Stop', playPause: 'Play/Pause' } as Record<string, string>)[b] ?? 'Media';
}

/** The process name a control's first command targets (for app-icon lookup), if any. */
export function processNameOf(cmds: Commands | null | undefined): string | undefined {
  const pn = (cmds?.commands?.[0] as any)?.processName;
  return Array.isArray(pn) && pn.length ? String(pn[0]) : undefined;
}

/** Short human label for a control's command list (for chips / labels). */
export function shortLabel(cmds: Commands | null | undefined): string {
  const first = cmds?.commands?.[0];
  if (!first) return '';
  const typeName = (first._type?.split('.').pop() ?? '').replace(/^Command/, '');
  const fn = SHORT[`Command${typeName}`];
  const label = fn?.(first);
  if (label) return label;
  // fall back to spaced type name
  return typeName.replace(/([a-z])([A-Z])/g, '$1 $2');
}

export type { ColorVisual };
