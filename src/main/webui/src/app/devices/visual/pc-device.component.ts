import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { DeviceStateService } from '../../services/device-state.service';
import { DeviceCapabilitiesService } from '../../services/device-capabilities.service';
import { IntegrationDataService } from '../../features/commands/integration-data.service';
import { Commands, LightingConfig } from '../../models/generated/backend.types';
import { PcKnobComponent } from './pc-knob.component';
import { PcFaderComponent } from './pc-fader.component';
import { PcLogoComponent } from './pc-logo.component';
import { analogPct, controlVisual, knobColor, processNameOf, shortLabel } from './device-visual.util';
import { resolveVolGrad } from '../pcpanel/lighting-animation';
import { ControlClick, ControlKind } from './control-click';

export type { ControlClick, ControlKind } from './control-click';

/** One assignment chip. `tag` marks a non-turn slot (P = single-press, PP = double-press);
 *  the turn chip has no tag but may carry the controlled app icon. `baseLayer` marks a fallback
 *  inherited from the device's base-layer profile (the active profile has nothing on this slot). */
interface ChipVM { tag?: string; icon?: string; label: string; slot?: 'press' | 'dblpress'; baseLayer?: boolean; }
interface KnobVM { index: number; label: string; pct: number; color: string; off: boolean; selected: boolean; chips: ChipVM[]; anim: string; dur: string; bmin: number; }
interface FaderVM { index: number; label: string; pct: number; colors: string[]; labelColor: string; off: boolean; selected: boolean; chips: ChipVM[]; anim: string; dur: string; bmin: number; }

function nearBlack(hex: string): boolean {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex || '');
  if (!m) return false;
  const n = parseInt(m[1], 16);
  return (0.299 * (n >> 16 & 255) + 0.587 * (n >> 8 & 255) + 0.114 * (n & 255)) < 18;
}

/** The Wave Link channel id a command acts on (so its Elgato icon can be looked up), or undefined. */
function wlChannelIdOf(cmd: Record<string, any>): string | undefined {
  switch (cmd['_type']?.split('.').pop() ?? '') {
    case 'CommandWaveLinkChannelEffect': return cmd['channelId'];
    case 'CommandWaveLinkAddFocusToChannel': return cmd['id'];
    case 'CommandWaveLinkChangeLevel':
    case 'CommandWaveLinkChangeMute':
      return cmd['commandType'] === 'Channel' || cmd['commandType'] === 'Mix' ? cmd['id1'] : undefined;
    default: return undefined;
  }
}

/**
 * The on-screen device — the focal point of the editor. Renders the live
 * snapshot (knob positions, LED colors, animation) for Pro / Mini / RGB and
 * emits a {@link ControlClick} when a control is clicked or right-clicked.
 */
@Component({
  selector: 'pc-device',
  standalone: true,
  imports: [NgTemplateOutlet, PcKnobComponent, PcFaderComponent, PcLogoComponent],
  template: `
    @if (snap(); as s) {
      <div class="chassis" [class.flat]="flat()">
        <!-- PRO: 5 knobs (brick 2+3) + 4 sliders + logo -->
        @if (isPro()) {
          <div class="pro-knobs">
            <div class="row top">
              @for (k of knobs().slice(0, 2); track k.index) {
                <ng-container [ngTemplateOutlet]="knobTpl" [ngTemplateOutletContext]="{ $implicit: k }"></ng-container>
              }
            </div>
            <div class="row bottom">
              @for (k of knobs().slice(2); track k.index) {
                <ng-container [ngTemplateOutlet]="knobTpl" [ngTemplateOutletContext]="{ $implicit: k }"></ng-container>
              }
            </div>
          </div>
          <div class="sliders">
            @for (f of faders(); track f.index) {
              <div class="slot fader-slot" role="button" tabindex="0" [attr.aria-label]="'Configure ' + f.label"
                   (click)="emit('slider', f.index, false, $event)" (contextmenu)="emit('slider', f.index, true, $event)"
                   (keydown.enter)="emit('slider', f.index, false, $event)" (keydown.space)="emit('slider', f.index, false, $event); $event.preventDefault()">
                <span class="s-label" [style.color]="f.off ? 'var(--text-3)' : f.labelColor"
                      [style.text-shadow]="f.off ? 'none' : '0 0 9px ' + f.labelColor + 'AA'">{{ f.label }}</span>
                <pc-fader [value]="f.pct" [colors]="f.colors" [off]="f.off" [selected]="f.selected"
                          [height]="faderHeight()" [animClass]="f.anim" [animDuration]="f.dur" [breathMin]="f.bmin"></pc-fader>
                @if (showChips() && f.chips.length) {
                  <div class="chips">
                    @for (c of f.chips; track $index) {
                      <span class="chip" [class.base]="c.baseLayer" [attr.title]="c.baseLayer ? 'From base layer — click to edit there' : null" (click)="chipClick(f.index, c, $event)">
                        @if (c.tag) { <span class="chip-tag">{{ c.tag }}</span> }
                        @if (c.icon) { <img class="chip-ico" [src]="c.icon" alt=""> }
                        <span class="chip-txt">{{ c.label }}</span>
                      </span>
                    }
                  </div>
                }
              </div>
            }
          </div>
          @if (hasLogo()) {
            <div class="logo-wrap" role="button" tabindex="0" aria-label="Configure logo lighting"
                 (click)="emit('logo', 0, false, $event)" (contextmenu)="emit('logo', 0, true, $event)"
                 (keydown.enter)="emit('logo', 0, false, $event)" (keydown.space)="emit('logo', 0, false, $event); $event.preventDefault()">
              <pc-logo [color]="logo().color" [off]="logo().off" [animClass]="logo().anim"
                       [animDuration]="logo().dur" [breathMin]="logo().bmin"></pc-logo>
            </div>
          }
        } @else {
          <!-- MINI / RGB: 4 knobs in a row -->
          <div class="row mini-row">
            @for (k of knobs(); track k.index) {
              <ng-container [ngTemplateOutlet]="knobTpl" [ngTemplateOutletContext]="{ $implicit: k }"></ng-container>
            }
          </div>
        }
      </div>
    }

    <ng-template #knobTpl let-k>
      <div class="slot knob-slot" role="button" tabindex="0" [attr.aria-label]="'Configure ' + k.label"
           (click)="emit('dial', k.index, false, $event)" (contextmenu)="emit('dial', k.index, true, $event)"
           (keydown.enter)="emit('dial', k.index, false, $event)" (keydown.space)="emit('dial', k.index, false, $event); $event.preventDefault()">
        <pc-knob [value]="k.pct" [color]="k.color" [off]="k.off" [selected]="k.selected" [size]="knobSize()"
                 [animClass]="k.anim" [animDuration]="k.dur" [breathMin]="k.bmin"></pc-knob>
        @if (showLabels()) { <span class="k-label">{{ k.label }}</span> }
        @if (showChips() && k.chips.length) {
          <div class="chips">
            @for (c of k.chips; track $index) {
              <span class="chip" [class.base]="c.baseLayer" [attr.title]="c.baseLayer ? 'From base layer — click to edit there' : null" (click)="chipClick(k.index, c, $event)">
                @if (c.tag) { <span class="chip-tag">{{ c.tag }}</span> }
                @if (c.icon) { <img class="chip-ico" [src]="c.icon" alt=""> }
                <span class="chip-txt">{{ c.label }}</span>
              </span>
            }
          </div>
        }
      </div>
    </ng-template>
  `,
  styles: [`
    :host { display: inline-block; }
    .chassis {
      background: linear-gradient(180deg, #121419, #0C0D11); border: 1px solid var(--line);
      border-radius: var(--r-chassis); padding: 30px 40px; box-shadow: var(--sh-chassis);
      display: flex; flex-direction: column; align-items: center; gap: 24px;
    }
    .chassis.flat { box-shadow: 0 18px 50px rgba(0,0,0,0.5); }
    .pro-knobs { display: flex; flex-direction: column; align-items: center; gap: 22px; width: 100%; }
    .row { display: flex; }
    .row.top { justify-content: space-evenly; width: 100%; }
    .row.bottom { justify-content: space-between; width: 100%; }
    .row.mini-row { gap: 24px; }
    .sliders { display: flex; justify-content: center; gap: 30px; }
    .slot { display: flex; flex-direction: column; align-items: center; gap: 6px; cursor: pointer; outline: none; border-radius: 10px; }
    .slot:focus-visible, .logo-wrap:focus-visible { outline: none; box-shadow: 0 0 0 2px var(--accent-border-2); }
    .logo-wrap { outline: none; border-radius: 10px; }
    .fader-slot { gap: 9px; width: 65px; }
    .k-label, .s-label { font-family: var(--font-mono); font-size: 9.5px; letter-spacing: 0.04em; color: #8A909B; }
    /* Zero width so a wide chip overflows symmetrically into the inter-control gaps
       instead of widening the slot (and the whole chassis); height is still reserved. */
    .chips { display: flex; flex-direction: column; align-items: center; gap: 4px; width: 0; }
    /* Sliders sit on a tighter pitch than knobs, so cap their chips at 90px. NOTE: this does not yet
       visually clamp — a flex item ignores max-width when its container's inner width is 0 (the width:0
       trick that stops chips widening the chassis), falling back to nowrap max-content. Revisit. */
    .fader-slot .chip { max-width: 90px; }
    .chip {
      display: inline-flex; align-items: center; gap: 4px;
      font-family: var(--font-ui); font-size: 9.5px; color: var(--text-soft); background: var(--raised);
      border: 1px solid var(--raised-line); padding: 2px 7px; border-radius: var(--r-pill); max-width: 120px;
    }
    /* Inherited-from-base-layer chips read as secondary: dashed outline, dimmed, italic label. */
    .chip.base { border-style: dashed; border-color: var(--line-2); background: transparent; opacity: 0.8; }
    .chip.base .chip-txt { font-style: italic; color: var(--text-3); }
    .chip-tag { font-family: var(--font-mono); font-size: 8px; font-weight: 600; letter-spacing: 0.03em; color: var(--accent); flex: none; }
    .chip-ico { width: 12px; height: 12px; border-radius: 3px; object-fit: contain; flex: none; }
    .chip-txt { font-size: 8.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
    .logo-wrap { cursor: pointer; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PcDeviceComponent {
  private readonly state = inject(DeviceStateService);
  private readonly capsService = inject(DeviceCapabilitiesService);
  private readonly integrations = inject(IntegrationDataService);

  readonly serial = input.required<string>();
  readonly showLabels = input<boolean>(true);
  readonly showChips = input<boolean>(false);
  readonly flat = input<boolean>(false);
  readonly knobSize = input<number>(56);
  readonly faderHeight = input<number>(128);
  readonly selectedKind = input<ControlKind | null>(null);
  readonly selectedIndex = input<number>(-1);

  readonly controlClick = output<ControlClick>();

  readonly snap = this.state.snapshotFor(this.serial);
  private readonly caps = this.capsService.forSerial(this.serial);
  /** "Pro layout" (has sliders + logo) — derived from the descriptor, not the enum.
   *  The debug device-type override flows through the capabilities descriptor. */
  readonly isPro = this.caps.isProLayout;
  readonly hasLogo = this.caps.hasLogo;

  private readonly config = computed<LightingConfig | null>(() => this.snap()?.lightingConfig ?? null);

  /** process-name (lowercased) → icon data-URI, for assignment chips. */
  private readonly processIconMap = computed(() => {
    const m = new Map<string, string>();
    for (const p of this.integrations.processes.value() ?? []) {
      if (p.name && p.icon) m.set(p.name.toLowerCase(), p.icon);
    }
    return m;
  });

  /** App icon for a process / overlay-icon name, if one is known. */
  private appIcon(name: string | undefined): string | undefined {
    return name ? this.processIconMap().get(name.toLowerCase()) : undefined;
  }

  /** The per-control overlay-icon override as an <img> src: a process name resolves to its app
   *  icon; an already-image value (data-URI / URL / asset path) is used directly. */
  private overlayIconSrc(overlay: string | undefined): string | undefined {
    if (!overlay) return undefined;
    if (overlay.startsWith('data:') || overlay.startsWith('http') || overlay.startsWith('/')) return overlay;
    return this.appIcon(overlay);
  }

  /** Best icon for a slot's command, mirroring the Windows overlay: the controlled app's icon
   *  for a process command, or the Elgato channel image for Wave Link. Returns undefined for
   *  commands with no stable icon (e.g. focus-volume, whose icon tracks the live focused app
   *  and so is deliberately left blank in this static preview). */
  private commandIcon(cmds: Commands | null | undefined): string | undefined {
    const cmd = cmds?.commands?.[0] as Record<string, any> | undefined;
    if (!cmd) return undefined;
    const appIc = this.appIcon(processNameOf(cmds));
    if (appIc) return appIc;
    const channelId = wlChannelIdOf(cmd);
    if (channelId) return this.integrations.wlChannels().find(c => c.id === channelId)?.image || undefined;
    return undefined;
  }

  /** One slot's chip: the active profile's command, or — when the active profile leaves the slot
   *  blank — the base-layer fallback (flagged so it renders distinctly and edits the base layer).
   *  Each carries the icon the overlay would show (per-control overlay-icon override wins). */
  private slotChip(active: Commands | undefined, base: Commands | undefined,
                   opts: { tag?: string; slot?: 'press' | 'dblpress'; activeOverlay?: string; baseOverlay?: string }): ChipVM | null {
    const data = this.integrations;
    const activeLabel = shortLabel(active, data);
    if (activeLabel) {
      return { tag: opts.tag, label: activeLabel, icon: opts.activeOverlay ?? this.commandIcon(active), slot: opts.slot };
    }
    const baseLabel = shortLabel(base, data);
    if (baseLabel) {
      return { tag: opts.tag, label: baseLabel, icon: opts.baseOverlay ?? this.commandIcon(base), slot: opts.slot, baseLayer: true };
    }
    return null;
  }

  /** Assignment chips for a knob: turn (dial), single-press and double-press, each shown when the
   *  active profile OR the base layer has a command for that slot. */
  private knobChips(i: number): ChipVM[] {
    const prof = this.snap()?.currentProfileSnapshot;
    const base = this.snap()?.baseLayerSnapshot;
    const key = String(i);
    const activeOverlay = this.overlayIconSrc(prof?.knobSettings?.[key]?.overlayIcon);
    const baseOverlay = this.overlayIconSrc(base?.knobSettings?.[key]?.overlayIcon);
    const out: ChipVM[] = [];
    const push = (active: Commands | undefined, baseCmd: Commands | undefined, tag?: string, slot?: 'press' | 'dblpress') => {
      const chip = this.slotChip(active, baseCmd, { tag, slot, activeOverlay, baseOverlay });
      if (chip) out.push(chip);
    };
    push(prof?.dialData?.[key], base?.dialData?.[key]);
    push(prof?.buttonData?.[key], base?.buttonData?.[key], 'P', 'press');
    push(prof?.dblButtonData?.[key], base?.dblButtonData?.[key], 'PP', 'dblpress');
    return out;
  }

  /** Sliders map only their analog movement, so just the single turn chip (active or base-layer). */
  private faderChips(analogIdx: number): ChipVM[] {
    const prof = this.snap()?.currentProfileSnapshot;
    const base = this.snap()?.baseLayerSnapshot;
    const key = String(analogIdx);
    const chip = this.slotChip(prof?.dialData?.[key], base?.dialData?.[key], {
      activeOverlay: this.overlayIconSrc(prof?.knobSettings?.[key]?.overlayIcon),
      baseOverlay: this.overlayIconSrc(base?.knobSettings?.[key]?.overlayIcon),
    });
    return chip ? [chip] : [];
  }

  readonly knobs = computed<KnobVM[]>(() => {
    const s = this.snap();
    if (!s) return [];
    const total = this.caps.knobCount();
    const cfg = this.config();
    const out: KnobVM[] = [];
    for (let i = 0; i < total; i++) {
      const pct = analogPct(s.analogValues?.[i]);
      // A volume-gradient knob's colour is a $VOLGRAD! token resolved against the live value here;
      // a plain colour (static / mute-override) passes through unchanged.
      const raw = knobColor(s.dialColors, i, total, cfg, '#2A2E37');
      const color = resolveVolGrad(raw, pct) ?? raw;
      const vis = controlVisual(color, cfg, '#2A2E37');
      const off = nearBlack(vis.fill);
      const selected = this.selectedKind() === 'dial' && this.selectedIndex() === i;
      out.push({
        index: i, label: `K${i + 1}`, pct,
        color: vis.fill, off, selected,
        chips: this.knobChips(i),
        anim: vis.animClass, dur: vis.animDuration, bmin: vis.breathMin,
      });
    }
    return out;
  });

  readonly faders = computed<FaderVM[]>(() => {
    const s = this.snap();
    const sliders = this.caps.sliders();
    if (!s || !sliders.length) return [];
    const cfg = this.config();
    const out: FaderVM[] = [];
    for (let j = 0; j < sliders.length; j++) {
      const analogIdx = sliders[j].index;
      const segs = (s.sliderColors?.[j] ?? []).map(c => controlVisual(c, cfg, '#2A2E37').fill);
      const first = segs[0] ?? '#2A2E37';
      const vis = controlVisual(s.sliderColors?.[j]?.[0], cfg, '#2A2E37');
      const labelColor = s.sliderLabelColors?.[j] || first;
      const off = segs.every(nearBlack);
      out.push({
        index: analogIdx, label: `S${j + 1}`, pct: analogPct(s.analogValues?.[analogIdx]),
        colors: segs.length ? segs : ['#2A2E37'], labelColor,
        off, selected: this.selectedKind() === 'slider' && this.selectedIndex() === analogIdx,
        chips: this.faderChips(analogIdx),
        anim: vis.animClass, dur: vis.animDuration, bmin: vis.breathMin,
      });
    }
    return out;
  });

  readonly logo = computed(() => {
    const s = this.snap();
    const cfg = this.config();
    const vis = controlVisual(s?.logoColor, cfg, '#FFB020');
    return { color: vis.fill, off: nearBlack(vis.fill), anim: vis.animClass, dur: vis.animDuration, bmin: vis.breathMin };
  });

  emit(kind: ControlKind, index: number, contextClicked: boolean, event: Event): void {
    if (contextClicked) event.preventDefault();
    this.controlClick.emit({ kind, index, contextClicked, event });
  }

  /** A press / double-press chip opens the control on that slot's tab; a base-layer chip opens that
   *  command in the base-layer profile. An active turn chip has neither, so it falls through to the
   *  control's own click (configure the active profile on the default Rotate / Slide tab). */
  chipClick(index: number, chip: ChipVM, event: Event): void {
    if (!chip.slot && !chip.baseLayer) return;
    event.stopPropagation();
    this.controlClick.emit({
      kind: 'dial', index, contextClicked: false, slot: chip.slot,
      profile: chip.baseLayer ? this.snap()?.baseLayerSnapshot?.name : undefined,
      event,
    });
  }
}
