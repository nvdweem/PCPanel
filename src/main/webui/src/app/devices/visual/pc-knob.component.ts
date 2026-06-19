import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * "Modern" style knob graphic: LED ring + brushed cap + value pointer.
 * value is 0–100; pointer angle = -135° + value × 2.7 (a 270° sweep, gap at
 * the bottom). Colour is the resolved LED hex; `selected` overrides to red,
 * `off` to an unlit grey. Animated modes are driven by the anim* inputs.
 */
@Component({
  selector: 'pc-knob',
  standalone: true,
  template: `
    <div class="knob" [style.width.px]="size()" [style.height.px]="size()">
      <div class="ring" [class]="off() || selected() ? '' : animClass()"
           [style.border-color]="ringColor()"
           [style.box-shadow]="ringGlow()"
           [style.--anim-duration]="animDuration()"
           [style.--breath-min]="breathMin()"></div>
      <div class="cap" [style.inset.px]="inset()" [class.off]="off()"></div>
      <div class="pointer-wrap" [style.inset.px]="inset()" [style.transform]="'rotate(' + angle() + 'deg)'">
        <div class="pointer"></div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: inline-flex; }
    .knob { position: relative; }
    .ring { position: absolute; inset: 0; border-radius: 50%; border-style: solid; border-width: 3px; }
    .cap {
      position: absolute; border-radius: 50%; border: 1px solid var(--canvas);
      background: radial-gradient(circle at 38% 30%, #3C424C, #1A1D23 72%);
      box-shadow: inset 0 2px 4px rgba(255,255,255,0.07), inset 0 -3px 7px rgba(0,0,0,0.65);
    }
    .cap.off { background: radial-gradient(circle at 38% 30%, #2C313A, #15181D 72%); }
    .pointer-wrap { position: absolute; display: flex; justify-content: center; }
    .pointer { width: 3px; height: 42%; margin-top: 3px; border-radius: 2px; background: #0E0F12; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PcKnobComponent {
  readonly value = input<number>(0);          // 0–100
  readonly color = input<string>('#FFB020');
  readonly selected = input<boolean>(false);
  readonly off = input<boolean>(false);
  readonly size = input<number>(56);
  readonly glow = input<boolean>(true);
  readonly animClass = input<string>('');
  readonly animDuration = input<string>('0s');
  readonly breathMin = input<number>(0.18);

  readonly angle = computed(() => -135 + Math.max(0, Math.min(100, this.value())) * 2.7);
  readonly inset = computed(() => Math.round(this.size() * 0.16));

  readonly ringColor = computed(() =>
    this.off() ? 'var(--raised-line)' : this.selected() ? 'var(--selected)' : this.color());

  readonly ringGlow = computed(() => {
    if (this.off() || !this.glow()) return 'none';
    if (this.selected()) return '0 0 20px rgba(255,77,77,0.6)';
    return `0 0 16px ${this.color()}5A`;
  });
}
