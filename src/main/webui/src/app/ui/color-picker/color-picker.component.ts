import { ChangeDetectionStrategy, Component, computed, effect, ElementRef, input, model, signal, untracked, viewChild } from '@angular/core';
import { OverlayModule } from '@angular/cdk/overlay';
import { hsvToRgb, parseHex, rgbToHex, rgbToHsv } from './color-utils';

const LED_PALETTE = ['#FF4D4D', '#3BE06A', '#3B6BFF', '#FF36C8', '#28E0E0', '#F2D024', '#A06BFF', '#FF6B3D', '#43C08A'];

/**
 * Custom RGBA color picker. Renders a swatch trigger; clicking opens a panel
 * with a saturation/value box, hue (+ optional alpha) strips, hex/RGB readout
 * and the LED preset palette. Two-way bind [(value)] with a hex string.
 */
@Component({
  selector: 'pc-color-picker',
  standalone: true,
  imports: [OverlayModule],
  template: `
    <button type="button" [class]="label() ? 'cp-block' : 'swatch'" cdkOverlayOrigin #t="cdkOverlayOrigin"
            [disabled]="disabled()"
            [style.width.px]="label() ? null : swatchSize()" [style.height.px]="label() ? null : swatchSize()"
            [style.background]="label() ? null : value()" (click)="open.set(!open())">
      @if (label()) {
        <span class="cp-label">{{ label() }}</span>
        <span class="cp-swatch" [style.background]="value()"></span>
      }
    </button>

    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="t" [cdkConnectedOverlayOpen]="open()"
                 [cdkConnectedOverlayHasBackdrop]="true" cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                 [cdkConnectedOverlayOffsetY]="6" (backdropClick)="open.set(false)" (detach)="open.set(false)">
      <div class="panel">
        <div class="row">
          <div class="left">
            <div #sv class="sv" [style.background]="svBg()" (pointerdown)="svDown($event)">
              <div class="sv-cursor" [style.left.%]="hsv().s * 100" [style.top.%]="(1 - hsv().v) * 100"></div>
            </div>
            <div #hue class="strip hue" (pointerdown)="hueDown($event)">
              <div class="strip-cursor" [style.left.%]="hsv().h / 360 * 100"></div>
            </div>
            @if (alpha()) {
              <div #al class="strip alpha" [style.--c]="opaqueHex()" (pointerdown)="alphaDown($event)">
                <div class="strip-cursor" [style.left.%]="a() * 100"></div>
              </div>
            }
          </div>
          <div class="right">
            <div class="preview" [style.background]="value()"></div>
            <div class="hex">
              <span class="hash">#</span>
              <input class="hex-in" [value]="hexNoHash()" (change)="onHexInput($event)" spellcheck="false" maxlength="8">
            </div>
            <div class="rgb">
              <div class="rgb-cell">{{ round(rgb().r) }}</div>
              <div class="rgb-cell">{{ round(rgb().g) }}</div>
              <div class="rgb-cell">{{ round(rgb().b) }}</div>
            </div>
            <div class="presets">
              @for (p of presets(); track p) {
                <button type="button" class="preset" [style.background]="p" (click)="setHex(p)"></button>
              }
            </div>
          </div>
        </div>
      </div>
    </ng-template>
  `,
  styles: [`
    :host { display: inline-flex; }
    .swatch {
      border: 1px solid var(--raised-line); border-radius: var(--r-sm); cursor: pointer; padding: 0;
      box-shadow: inset 0 0 0 1px rgba(0,0,0,0.2);
    }
    /* Labeled block: the whole outlined row is clickable */
    .cp-block {
      display: flex; align-items: center; justify-content: space-between; width: 100%;
      background: #121419; border: 1px solid var(--line); border-radius: var(--r-sm);
      padding: 8px 11px; cursor: pointer; color: var(--text-2); font-family: var(--font-ui); font-size: 11.5px;
    }
    .cp-block:hover { border-color: var(--line-2); }
    .cp-block:disabled, .swatch:disabled { opacity: .5; cursor: default; }
    .cp-block:disabled:hover { border-color: var(--line); }
    .cp-swatch { width: 22px; height: 22px; border-radius: 5px; border: 1px solid var(--raised-line); flex: none; box-shadow: inset 0 0 0 1px rgba(0,0,0,0.2); }
    :host:has(.cp-block) { display: flex; width: 100%; }
    .panel {
      background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-lg);
      padding: 14px; box-shadow: var(--sh-pop); width: 280px;
    }
    .row { display: flex; gap: 16px; }
    .left { flex: 1; }
    .sv {
      position: relative; height: 108px; border-radius: var(--r-md); border: 1px solid var(--line);
      cursor: crosshair; touch-action: none;
    }
    .sv-cursor {
      position: absolute; width: 13px; height: 13px; border-radius: 50%; border: 2px solid #fff;
      box-shadow: 0 0 0 1px rgba(0,0,0,0.4); transform: translate(-50%, -50%); pointer-events: none;
    }
    .strip { position: relative; height: 11px; border-radius: var(--r-sm); margin-top: 9px; cursor: pointer; touch-action: none; }
    .hue { background: linear-gradient(90deg,#FF4D4D,#FFB020,#3BE06A,#28C8E8,#3B6BFF,#A06BFF,#FF4D4D); }
    .alpha {
      background: linear-gradient(90deg, transparent, var(--c)),
                  repeating-conic-gradient(#3A3F4B 0% 25%, #23262E 0% 50%);
      background-size: auto, 8px 8px;
    }
    .strip-cursor {
      position: absolute; top: 50%; width: 5px; height: 15px; border-radius: 3px; background: #fff;
      box-shadow: 0 0 0 1px rgba(0,0,0,0.4); transform: translate(-50%, -50%); pointer-events: none;
    }
    .right { width: 96px; display: flex; flex-direction: column; gap: 8px; }
    .preview { height: 34px; border-radius: var(--r-sm); box-shadow: 0 0 14px rgba(0,0,0,0.4); }
    .hex { display: flex; align-items: center; gap: 6px; background: var(--input); border: 1px solid var(--raised-line); border-radius: 7px; padding: 6px 9px; }
    .hash { font-family: var(--font-mono); font-size: 11px; color: var(--text-3); }
    .hex-in { flex: 1; min-width: 0; background: transparent; border: none; outline: none; color: var(--text-1); font-family: var(--font-mono); font-size: 12px; }
    .rgb { display: flex; gap: 5px; }
    .rgb-cell { flex: 1; text-align: center; background: var(--input); border: 1px solid var(--raised-line); border-radius: var(--r-sm); padding: 5px 0; font-family: var(--font-mono); font-size: 11px; }
    .presets { display: grid; grid-template-columns: repeat(3, 1fr); gap: 5px; }
    .preset { height: 18px; border: none; border-radius: 5px; cursor: pointer; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ColorPickerComponent {
  readonly value = model<string>('#FFB020');
  readonly alpha = input<boolean>(false);
  readonly disabled = input<boolean>(false);
  readonly swatchSize = input<number>(30);
  readonly presets = input<string[]>(LED_PALETTE);
  /** When set, render a full-width labeled block (the whole row opens the picker). */
  readonly label = input<string>('');
  readonly open = model<boolean>(false);

  private readonly sv = viewChild<ElementRef<HTMLElement>>('sv');
  private readonly hue = viewChild<ElementRef<HTMLElement>>('hue');
  private readonly al = viewChild<ElementRef<HTMLElement>>('al');

  // Local editable HSV state, seeded from value() but not overwritten while dragging
  // (avoids hue snapping to 0 at s=0). Re-seeds via an effect when value changes externally.
  private readonly _hsv = signal(rgbToHsv(parseHex('#FFB020').rgb));
  private readonly _a = signal(1);
  private lastValue = '';

  readonly hsv = this._hsv.asReadonly();
  readonly a = this._a.asReadonly();
  readonly rgb = computed(() => hsvToRgb(this.hsv()));

  constructor() {
    effect(() => {
      const v = this.value();
      if (v === this.lastValue) return;
      this.lastValue = v;
      const p = parseHex(v);
      untracked(() => { this._hsv.set(rgbToHsv(p.rgb)); this._a.set(p.a); });
    });
  }
  readonly svBg = computed(() => `linear-gradient(to top,#000,transparent),linear-gradient(to right,#fff,hsl(${this.hsv().h},100%,50%))`);
  readonly opaqueHex = computed(() => rgbToHex(this.rgb()));
  readonly hexNoHash = computed(() => (this.alpha() ? rgbToHex(this.rgb(), this.a()) : rgbToHex(this.rgb())).replace('#', ''));

  private commit(): void {
    this.value.set(this.alpha() ? rgbToHex(this.rgb(), this._a()) : rgbToHex(this.rgb()));
    this.lastValue = this.value();
  }

  setHex(hex: string): void {
    const p = parseHex(hex);
    this._hsv.set(rgbToHsv(p.rgb));
    this._a.set(p.a);
    this.commit();
  }

  onHexInput(ev: Event): void { this.setHex((ev.target as HTMLInputElement).value); }

  round(n: number): number { return Math.round(n); }

  svDown(ev: PointerEvent): void { this.drag(ev, this.sv(), (fx, fy) => { const h = this._hsv(); this._hsv.set({ h: h.h, s: fx, v: 1 - fy }); }); }
  hueDown(ev: PointerEvent): void { this.drag(ev, this.hue(), (fx) => { const h = this._hsv(); this._hsv.set({ ...h, h: fx * 360 }); }); }
  alphaDown(ev: PointerEvent): void { this.drag(ev, this.al(), (fx) => this._a.set(fx)); }

  private drag(ev: PointerEvent, ref: ElementRef<HTMLElement> | undefined, set: (fx: number, fy: number) => void): void {
    if (!ref) return;
    const el = ref.nativeElement;
    el.setPointerCapture?.(ev.pointerId);
    const apply = (e: PointerEvent) => {
      const r = el.getBoundingClientRect();
      const fx = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
      const fy = Math.max(0, Math.min(1, (e.clientY - r.top) / r.height));
      set(fx, fy);
      this.commit();
    };
    apply(ev);
    const move = (e: PointerEvent) => apply(e);
    const up = () => { window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up); };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }
}
