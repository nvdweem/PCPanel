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
    return truncate(this.dialLabels().get(i) ?? '—', 9);
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
    return cmds.commands.map((c: Command) => (c['_type'] ?? '').split('.').pop() ?? '?').join(', ');
  }
}
