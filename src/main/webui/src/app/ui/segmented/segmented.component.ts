import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

export interface SegmentOption<T = string> { value: T; label: string; }

/** Inset segmented control (e.g. Basic/Advanced, Log/Linear, Horizontal/Vertical). */
@Component({
  selector: 'pc-segmented',
  standalone: true,
  template: `
    <div class="seg" role="tablist" [class.disabled]="disabled()">
      @for (opt of options(); track opt.value) {
        <button type="button" role="tab" class="seg-item" [class.active]="opt.value === value()"
                [disabled]="disabled()" [attr.aria-selected]="opt.value === value()" (click)="select(opt.value)">
          {{ opt.label }}
        </button>
      }
    </div>
  `,
  styles: [`
    .seg {
      display: inline-flex; background: var(--input); border: 1px solid var(--line);
      border-radius: var(--r-md); padding: 3px; gap: 2px;
    }
    .seg-item {
      border: none; background: transparent; color: var(--text-2);
      font-size: 11.5px; padding: 5px 11px; border-radius: var(--r-sm); cursor: pointer;
      font-family: var(--font-ui); transition: background .12s, color .12s; white-space: nowrap;
    }
    .seg-item:hover { color: var(--text-1); }
    .seg-item.active { background: var(--line); color: var(--text-1); }
    .seg.disabled { opacity: .5; pointer-events: none; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SegmentedComponent<T = string> {
  readonly options = input.required<SegmentOption<T>[]>();
  readonly value = model<T>();
  readonly disabled = input<boolean>(false);

  select(v: T): void {
    if (this.disabled()) return;
    this.value.set(v);
  }
}
