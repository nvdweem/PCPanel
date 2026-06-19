import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Pro lit-logo tile: stacked "PC" / "Panel" wordmark with an amber glow and a
 * breathing animation. Colour follows the logo LED; off = unlit.
 */
@Component({
  selector: 'pc-logo',
  standalone: true,
  template: `
    <div class="logo" [style.width.px]="width()" [style.height.px]="height()"
         [style.background]="bg()" [style.box-shadow]="shadow()" [class]="off() ? '' : animClass()"
         [style.--anim-duration]="animDuration()" [style.--breath-min]="breathMin()">
      <span class="wordmark" [style.color]="off() ? 'var(--text-3)' : color()">
        <span class="pc">PC</span>
        <span class="panel">Panel</span>
      </span>
    </div>
  `,
  styles: [`
    :host { display: inline-flex; }
    .logo { border-radius: 10px; display: flex; align-items: center; justify-content: center; }
    .wordmark { font-family: var(--font-display); display: flex; flex-direction: column; align-items: center; line-height: 0.76; }
    .pc { font-weight: 800; font-size: 15px; letter-spacing: 0.2em; margin-right: -0.2em; }
    .panel { font-weight: 600; font-size: 8.5px; letter-spacing: 0.03em; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PcLogoComponent {
  readonly color = input<string>('#FFB020');
  readonly off = input<boolean>(false);
  readonly width = input<number>(60);
  readonly height = input<number>(48);
  readonly breathe = input<boolean>(true);
  readonly animClass = input<string>('');
  readonly animDuration = input<string>('0s');
  readonly breathMin = input<number>(0.18);

  readonly bg = computed(() =>
    this.off() ? 'var(--panel)' : `radial-gradient(circle at 50% 40%, ${this.color()}38, var(--panel) 75%)`);

  readonly shadow = computed(() =>
    this.off() ? 'inset 0 0 0 1px var(--line)' : `inset 0 0 0 1px ${this.color()}66, 0 0 16px ${this.color()}40`);
}
