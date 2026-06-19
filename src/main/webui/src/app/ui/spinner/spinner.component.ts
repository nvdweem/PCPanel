import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Amber ring spinner. */
@Component({
  selector: 'pc-spinner',
  standalone: true,
  template: `<span class="sp" [style.width.px]="size()" [style.height.px]="size()"
                  [style.border-width.px]="thickness()"></span>`,
  styles: [`
    :host { display: inline-flex; line-height: 0; }
    .sp {
      display: inline-block; border-radius: 50%;
      border-style: solid; border-color: var(--line); border-top-color: var(--accent);
      animation: pcp-spin .8s linear infinite;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpinnerComponent {
  readonly size = input<number>(22);
  readonly thickness = input<number>(2.5);
}
