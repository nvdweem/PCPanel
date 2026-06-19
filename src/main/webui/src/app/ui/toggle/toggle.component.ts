import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

/** Amber pill toggle switch. Two-way bind with [(value)]. */
@Component({
  selector: 'pc-toggle',
  standalone: true,
  template: `
    <button type="button" class="tg" [class.on]="value()" [class.disabled]="disabled()"
            [class.sm]="size() === 'sm'" [class.lg]="size() === 'lg'"
            [attr.aria-pressed]="value()" (click)="toggle()">
      <span class="knob"></span>
    </button>
  `,
  styles: [`
    :host { display: inline-flex; }
    .tg {
      --w: 34px; --h: 19px; --k: 15px;
      position: relative; width: var(--w); height: var(--h); flex: none;
      border: none; padding: 0; border-radius: var(--r-pill); cursor: pointer;
      background: var(--raised-line); transition: background .15s ease;
    }
    .tg.sm { --w: 30px; --h: 17px; --k: 13px; }
    .tg.lg { --w: 38px; --h: 22px; --k: 18px; }
    .tg.on { background: var(--accent); box-shadow: 0 0 12px var(--accent-glow); }
    .tg.disabled { opacity: .5; cursor: not-allowed; }
    .knob {
      position: absolute; top: 2px; left: 2px; width: var(--k); height: var(--k);
      border-radius: 50%; background: var(--text-3); transition: left .15s ease, background .15s ease;
    }
    .tg.on .knob { left: calc(100% - var(--k) - 2px); background: var(--accent-ink); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToggleComponent {
  readonly value = model<boolean>(false);
  readonly disabled = input<boolean>(false);
  readonly size = input<'sm' | 'md' | 'lg'>('md');

  toggle(): void {
    if (this.disabled()) return;
    this.value.set(!this.value());
  }
}
