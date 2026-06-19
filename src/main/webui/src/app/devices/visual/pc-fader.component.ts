import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * "Modern" style fader: inset track, LED fill from the bottom (height = value%)
 * and a brushed thumb. `colors` is the per-segment color list from the snapshot
 * (`sliderColors[i]`); a single distinct color renders the led→led88 design
 * gradient, multiple distinct colors render a vertical gradient (static gradient
 * lighting). `selected` adds a red thumb ring; `off` greys it out.
 */
@Component({
  selector: 'pc-fader',
  standalone: true,
  template: `
    <div class="fader" [style.width.px]="width()" [style.height.px]="height()">
      <div class="track"></div>
      <div class="fill" [class]="off() ? '' : animClass()"
           [style.height.%]="clamped()"
           [style.background]="fillBg()"
           [style.box-shadow]="fillGlow()"
           [style.--anim-duration]="animDuration()"
           [style.--breath-min]="breathMin()"></div>
      <div class="thumb" [style.bottom]="thumbBottom()" [style.box-shadow]="thumbShadow()"></div>
    </div>
  `,
  styles: [`
    :host { display: inline-flex; }
    .fader { position: relative; }
    .track { position: absolute; left: 50%; top: 0; bottom: 0; width: 8px; margin-left: -4px; border-radius: 6px; background: var(--canvas); box-shadow: inset 0 0 0 1px var(--line); }
    .fill { position: absolute; left: 50%; width: 8px; margin-left: -4px; bottom: 0; border-radius: 6px; }
    .thumb {
      position: absolute; left: 50%; width: 30px; height: 18px; margin-left: -15px; border-radius: 4px;
      background: linear-gradient(180deg, #4A4F59, #23262E);
      border-top: 1px solid #5A606B; border-bottom: 1px solid #0C0D10;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PcFaderComponent {
  readonly value = input<number>(0);            // 0–100
  readonly colors = input<string[]>(['#FFB020']);
  readonly selected = input<boolean>(false);
  readonly off = input<boolean>(false);
  readonly width = input<number>(32);
  readonly height = input<number>(128);
  readonly animClass = input<string>('');
  readonly animDuration = input<string>('0s');
  readonly breathMin = input<number>(0.18);

  readonly clamped = computed(() => Math.max(0, Math.min(100, this.value())));

  private readonly led = computed(() => this.colors()[0] || '#FFB020');

  readonly fillBg = computed(() => {
    if (this.off()) return 'var(--raised-line)';
    const cols = this.colors().filter(Boolean);
    const distinct = new Set(cols);
    if (cols.length > 1 && distinct.size > 1) {
      return `linear-gradient(0deg, ${cols.join(', ')})`; // bottom → top
    }
    const c = this.led();
    return `linear-gradient(180deg, ${c}, ${c}88)`;
  });

  readonly fillGlow = computed(() => this.off() ? 'none' : `0 0 14px ${this.led()}66`);

  readonly thumbBottom = computed(() => `calc(${this.clamped()}% - 9px)`);

  readonly thumbShadow = computed(() =>
    `0 2px 5px rgba(0,0,0,0.6), inset 0 0 0 1px ${this.selected() ? 'var(--selected)' : 'var(--line-2)'}`);
}
