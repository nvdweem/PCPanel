import { computed, Directive, inject, input } from '@angular/core';
import { DeviceStateService } from '../../services/device-state.service';
import { PcpanelCommandService } from './pcpanel-command.service';
import { knobRingColor, lightingAnimClass, lightingAnimDuration, lightingBreathMin } from './lighting-animation';
import { ensureCommands, mapRange } from '../../../shared';
import { ensureDialData } from './pcpanel.shared';
import { Command, Commands, LightingConfig, SingleKnobLightingConfig } from '../../models/generated/backend.types';
import { LightingVariant } from './command-config/command-config.component';

function truncate(s: string, n: number): string {
  return s.length > n ? s.substring(0, n) + '…' : s;
}

/**
 * Shared logic for the simple 4-knob PCPanel devices (Mini and RGB). Both are
 * self-contained components bound to a [serial]: they read the live device
 * snapshot from {@link DeviceStateService} (so knobs rotate with analog values
 * and rings reflect the lighting config) and open the command dialog on click.
 *
 * The Pro (5 knobs + 4 sliders + logo) keeps its own richer component.
 */
@Directive()
export abstract class PcpanelKnobDeviceBase {
  protected readonly deviceService = inject(DeviceStateService);
  protected readonly commandService = inject(PcpanelCommandService);

  readonly serial = input.required<string>();
  protected readonly device = this.deviceService.snapshotFor(this.serial);

  /** Mini and RGB both have exactly 4 knobs. */
  protected readonly knobIndices = [0, 1, 2, 3];

  /** Fallback ring color when the config provides none (device-specific accent). */
  protected abstract readonly defaultColor: string;
  /** How per-knob lighting is stored: Mini=knob (CUSTOM), RGB=rgb-single. */
  protected abstract readonly lightingVariant: LightingVariant;

  protected readonly analogValues = computed(() => this.device()?.analogValues ?? []);
  protected readonly lightingConfig = computed<LightingConfig | null>(() => this.device()?.lightingConfig ?? null);

  protected readonly dialLabels = computed<Map<number, string>>(() => {
    const data = this.device()?.currentProfileSnapshot?.dialData ?? {};
    const labels = new Map<number, string>();
    for (const [idx, cmds] of Object.entries(data)) {
      labels.set(Number(idx), this.formatCommands(cmds));
    }
    return labels;
  });

  protected readonly animClass = computed(() => {
    const cfg = this.lightingConfig();
    return cfg ? lightingAnimClass(cfg) : '';
  });

  protected readonly animDuration = computed(() => {
    const cfg = this.lightingConfig();
    return cfg ? lightingAnimDuration(cfg) : '0s';
  });

  protected readonly breathMin = computed(() => {
    const cfg = this.lightingConfig();
    return cfg ? lightingBreathMin(cfg) : 0.18;
  });

  /** Rotation: 0-255 → 30°–330°. */
  protected knobAngle(i: number): number {
    return mapRange(this.analogValues()[i] ?? 0, 0, 255, 30, 330);
  }

  protected ringColor(i: number): string {
    return knobRingColor(this.lightingConfig(), i, this.knobIndices.length, this.defaultColor);
  }

  protected label(i: number): string {
    return truncate(this.dialLabels().get(i) ?? '—', 18);
  }

  /** Full (untruncated) command label, for callers that shrink-to-fit instead of truncating. */
  protected commandLabel(i: number): string {
    return this.dialLabels().get(i) ?? '—';
  }

  /** Whether knob i has a command mapped (i.e. a real label, not the "—" placeholder). */
  protected hasCommand(i: number): boolean {
    const v = this.dialLabels().get(i);
    return v != null && v !== '—';
  }

  /**
   * Colour for the silk-screen device label ("VOLUME n"), which on the real
   * hardware is back-lit by the same LED as the knob. Use the knob's ring
   * colour when lit; fall back to a readable grey when that light is off
   * (near-black), so the label stays legible in the editor.
   */
  protected labelColor(i: number): string {
    const color = this.ringColor(i);
    return this.isLightOff(color) ? '#9a9a9a' : color;
  }

  private isLightOff(color: string): boolean {
    const rgb = this.parseColor(color);
    if (!rgb) return false;
    // Rec. 601 luma; treat a near-black (unlit) LED as "off".
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2] < 40;
  }

  private parseColor(color: string): [number, number, number] | null {
    const s = color.trim().toLowerCase();
    if (s === 'black') return [0, 0, 0];
    const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/.exec(s);
    if (!m) return null;
    const h = m[1].length === 3 ? m[1].replace(/(.)/g, '$1$1') : m[1];
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
  }

  /** Open the command/lighting dialog for a knob (which is also a press button). */
  protected edit(event: MouseEvent, idx: number, contextClicked: boolean): void {
    if (contextClicked) {
      event?.preventDefault();
    }
    const device = this.device();
    const snap = device?.currentProfileSnapshot;
    this.commandService.openCommandDialog({
      serial: this.serial(),
      controlIdx: idx,
      data: {
        title: `Knob ${idx + 1}`,
        controlType: 'dial',
        analog: ensureDialData(ensureCommands(true, snap?.dialData[String(idx)])),
        button: ensureCommands(true, snap?.buttonData[String(idx)]),
        dblButton: ensureCommands(true, snap?.dblButtonData[String(idx)]),
        knobSetting: snap?.knobSettings[String(idx)],
        lighting: this.knobLighting(device?.lightingConfig, idx),
        lightingVariant: this.lightingVariant,
      },
    });
  }

  private knobLighting(cfg: LightingConfig | null | undefined, idx: number): SingleKnobLightingConfig {
    if (this.lightingVariant === 'rgb-single') {
      return {
        mode: 'STATIC',
        color1: cfg?.individualColors?.[idx] || this.defaultColor,
        color2: '#000000',
      };
    }
    const config = cfg?.knobConfigs?.[idx];
    return {
      mode: config?.mode === 'VOLUME_GRADIENT' ? 'VOLUME_GRADIENT' : 'STATIC',
      color1: config?.color1 || '#ffffff',
      color2: config?.color2 || '#000000',
      muteOverrideColor: config?.muteOverrideColor,
      muteOverrideDeviceOrFollow: config?.muteOverrideDeviceOrFollow,
    };
  }

  private formatCommands(cmds: Commands | null | undefined): string {
    if (!cmds?.commands?.length) return '—';
    // Drop the redundant "Command" prefix from each type name (e.g.
    // CommandBrightness → Brightness) so the label fits the available space.
    return cmds.commands
      .map((c: Command) => ((c['_type'] ?? '').split('.').pop() ?? '?').replace(/^Command/, ''))
      .join(', ');
  }
}
