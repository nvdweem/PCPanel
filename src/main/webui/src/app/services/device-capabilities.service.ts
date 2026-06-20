import { computed, inject, Injectable, Signal } from '@angular/core';
import { DeviceStateService } from './device-state.service';
import { DebugService } from './debug.service';
import {
  AnalogInputSpec, DeviceDescriptor, LightOutputSpec,
} from '../models/generated/backend.types';

/** Descriptors for the three built-in PCPanel models, used as a fallback when a
 *  live snapshot has no descriptor (defensive) and to drive the debug override.
 *  These encode exactly today's geometry/lighting matrix (see plan §2.2). */
const PCPANEL_MODES_CUSTOM = ['ALL_COLOR', 'ALL_RAINBOW', 'ALL_WAVE', 'ALL_BREATH', 'CUSTOM'];
const PCPANEL_MODES_RGB = ['ALL_COLOR', 'SINGLE_COLOR', 'ALL_RAINBOW', 'ALL_WAVE', 'ALL_BREATH'];

function knobInputs(count: number, sourceMax: number): AnalogInputSpec[] {
  return Array.from({ length: count }, (_, i) => ({
    index: i, id: `knob${i}`, label: `K${i + 1}`, kind: 'KNOB' as const,
    sourceMin: 0, sourceMax, hasButton: true, lightOutputIndex: i,
  }));
}

function sliderInputs(count: number): AnalogInputSpec[] {
  return Array.from({ length: count }, (_, j) => ({
    index: j + 5, id: `slider${j}`, label: `S${j + 1}`, kind: 'SLIDER' as const,
    sourceMin: 0, sourceMax: 255, hasButton: false,
  }));
}

function dialLights(count: number): LightOutputSpec[] {
  return Array.from({ length: count }, (_, i) => ({
    index: i, id: `dial${i}`, label: `K${i + 1}`, colorModel: 'RGB' as const,
    group: 'DIAL' as const, supportedElementModes: [],
  }));
}

/** Build the built-in PCPanel descriptor for a model id (debug override / fallback). */
export function pcPanelDescriptor(kind: string): DeviceDescriptor {
  if (kind === 'PCPANEL_PRO') {
    const lights: LightOutputSpec[] = [
      ...dialLights(5),
      ...Array.from({ length: 4 }, (_, j) => ({ index: 5 + j, id: `slider${j}`, label: `S${j + 1}`, colorModel: 'RGB' as const, group: 'SLIDER' as const, supportedElementModes: [] })),
      ...Array.from({ length: 4 }, (_, j) => ({ index: 9 + j, id: `sliderLabel${j}`, label: `L${j + 1}`, colorModel: 'RGB' as const, group: 'SLIDER_LABEL' as const, supportedElementModes: [] })),
      { index: 13, id: 'logo', label: 'Logo', colorModel: 'RGB', group: 'LOGO', supportedElementModes: [] },
    ];
    return {
      providerId: 'pcpanel', deviceKindId: 'PCPANEL_PRO', displayName: 'PCPanel Pro',
      analogInputs: [...knobInputs(5, 255), ...sliderInputs(4)],
      digitalInputs: [], lightOutputs: lights, analogOutputs: [],
      globalLighting: { supportedModes: PCPANEL_MODES_CUSTOM, hasGlobalBrightness: true, brightnessMin: 0, brightnessMax: 100, firmwareAnimated: true },
    };
  }
  const rgb = kind === 'PCPANEL_RGB';
  return {
    providerId: 'pcpanel', deviceKindId: kind, displayName: rgb ? 'PCPanel RGB' : 'PCPanel Mini',
    analogInputs: knobInputs(4, rgb ? 100 : 255),
    digitalInputs: [], lightOutputs: dialLights(4), analogOutputs: [],
    globalLighting: { supportedModes: rgb ? PCPANEL_MODES_RGB : PCPANEL_MODES_CUSTOM, hasGlobalBrightness: true, brightnessMin: 0, brightnessMax: 100, firmwareAnimated: true },
  };
}

/**
 * Capability view over a device descriptor — the single source of truth for
 * device shape in the UI, replacing the duplicated `isPro`/`idx>=5`/knob-count
 * derivations that used to branch on the 3-value DeviceType enum.
 *
 * Call {@link forSerial} (or {@link forDescriptor}) to get a {@link DeviceCapabilities}
 * bundle of computed signals derived from the live snapshot's `descriptor`.
 */
@Injectable({ providedIn: 'root' })
export class DeviceCapabilitiesService {
  private readonly state = inject(DeviceStateService);
  private readonly debug = inject(DebugService);

  /** Resolve the descriptor for a serial: a debug override wins, then the live
   *  snapshot's descriptor, falling back to a synthesized PCPanel descriptor for
   *  the snapshot's deviceType (defensive — descriptor is non-optional on snapshots). */
  descriptorFor(serial: () => string | null | undefined): Signal<DeviceDescriptor | null> {
    const snap = this.state.snapshotFor(serial);
    return computed(() => {
      const override = this.debug.deviceTypeOverride();
      if (override) return pcPanelDescriptor(override);
      const s = snap();
      if (!s) return null;
      return s.descriptor ?? pcPanelDescriptor(s.deviceType);
    });
  }

  forSerial(serial: () => string | null | undefined): DeviceCapabilities {
    return new DeviceCapabilities(this.descriptorFor(serial));
  }

  forDescriptor(descriptor: Signal<DeviceDescriptor | null>): DeviceCapabilities {
    return new DeviceCapabilities(descriptor);
  }
}

/** Computed capability signals for one device, derived purely from its descriptor. */
export class DeviceCapabilities {
  constructor(readonly descriptor: Signal<DeviceDescriptor | null>) {}

  readonly analogInputs = computed<AnalogInputSpec[]>(() => this.descriptor()?.analogInputs ?? []);
  readonly knobs = computed<AnalogInputSpec[]>(() => this.analogInputs().filter(a => a.kind === 'KNOB'));
  readonly sliders = computed<AnalogInputSpec[]>(() => this.analogInputs().filter(a => a.kind === 'SLIDER'));
  readonly lightOutputs = computed<LightOutputSpec[]>(() => this.descriptor()?.lightOutputs ?? []);
  readonly analogOutputs = computed(() => this.descriptor()?.analogOutputs ?? []);

  readonly knobCount = computed(() => this.knobs().length);
  readonly sliderCount = computed(() => this.sliders().length);

  /** Analog index of the j-th slider (Pro slider 0 → index 5), or -1. */
  sliderIndexAt(position: number): number {
    return this.sliders()[position]?.index ?? -1;
  }

  /** Position of a slider among the sliders given its analog index (Pro idx 5 → 0), or -1. */
  sliderNumber(index: number): number {
    return this.sliders().findIndex(s => s.index === index);
  }

  isSlider(index: number): boolean {
    return this.sliders().some(s => s.index === index);
  }

  readonly providerId = computed(() => this.descriptor()?.providerId ?? '');
  readonly isPcPanel = computed(() => this.providerId() === 'pcpanel');
  /** "Pro layout": has at least one slider. Derived from the descriptor, not the enum string. */
  readonly isProLayout = computed(() => this.sliderCount() > 0);

  readonly globalLighting = computed(() => this.descriptor()?.globalLighting ?? null);
  readonly hasGlobalLighting = computed(() => this.globalLighting() != null);
  readonly hasGlobalBrightness = computed(() => this.globalLighting()?.hasGlobalBrightness ?? false);
  readonly supportedModes = computed<string[]>(() => this.globalLighting()?.supportedModes ?? []);
  readonly hasLogo = computed(() => this.lightOutputs().some(l => l.group === 'LOGO'));
  readonly hasRgbLights = computed(() => this.lightOutputs().some(l => l.colorModel === 'RGB' || l.colorModel === 'MONOCHROME'));

  readonly displayName = computed(() => this.descriptor()?.displayName ?? '');
}
