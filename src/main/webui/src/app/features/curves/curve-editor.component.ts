import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CurveDefinition, CurveMode, CurvePoint } from '../../models/generated/backend.types';
import { SegmentedComponent, SegmentOption, SliderComponent } from '../../ui';
import { CurveGraphComponent } from './curve-graph.component';
import { curveFn, LOG_AMOUNT, seedPoints } from './curve.util';

/**
 * Edits one curve's shape. A curve is either an Amount or hand-drawn points, never both, so the mode
 * switch picks which and only that control is shown. Switching to Custom the first time seeds the points
 * from the shape already on screen; the points are kept afterwards, so flipping back and forth does not
 * discard the drawing.
 */
@Component({
  selector: 'pc-curve-editor',
  standalone: true,
  imports: [SegmentedComponent, SliderComponent, CurveGraphComponent],
  template: `
    <div class="editor">
      <div class="left">
        <div class="plot">
          <div class="y-axis">output</div>
          <pc-curve-graph [curve]="curve()" [size]="270" [interactive]="curve().mode === 'points'"
                          (pointsChange)="setPoints($event)"></pc-curve-graph>
        </div>
        <div class="axis"><span>0%</span><span>dial position</span><span>100%</span></div>
      </div>

      <div class="right">
        <div class="pc-field">
          <div class="pc-field-label">Name</div>
          <input class="pc-field-input" type="text" [value]="curve().name ?? ''"
                 (input)="patch({ name: $any($event.target).value })">
        </div>

        <div class="mode">
          <pc-segmented [options]="modes" [value]="curve().mode ?? 'amount'"
                        (valueChange)="setMode($any($event))"></pc-segmented>
        </div>

        @if (curve().mode === 'points') {
          <div class="hint">
            Drag a point to shape the curve. Click the graph to add one, right-click a point to remove it.
            The end points slide up and down only.
          </div>
        } @else {
          <div class="pc-field-label">Amount</div>
          <pc-slider [value]="curve().amount" [min]="-100" [max]="100"
                     (valueChange)="patch({ amount: $event })"></pc-slider>
          <div class="scale"><span>−100</span><span>linear</span><span>+100</span></div>
          <div class="amount-note">
            <strong>{{ curve().amount > 0 ? '+' : '' }}{{ curve().amount }}</strong>
            <span>{{ amountNote() }}</span>
          </div>
        }

        <div class="readout">
          @for (sample of samples(); track sample.at) {
            <span>{{ sample.at }}% → {{ sample.out }}%</span>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .editor { display: flex; gap: 22px; flex-wrap: wrap; align-items: flex-start; }
    .left { flex: 0 0 auto; }
    .plot { display: flex; align-items: center; gap: 2px; }
    .y-axis {
      writing-mode: vertical-rl; transform: rotate(180deg);
      font-size: 10.5px; color: var(--text-2); letter-spacing: .04em;
    }
    .right { flex: 1 1 260px; min-width: 240px; display: flex; flex-direction: column; gap: 10px; }
    .axis, .scale {
      display: flex; justify-content: space-between; font-size: 10.5px;
      color: var(--text-2); margin-top: 2px; padding: 0 4px;
    }
    .mode { margin-top: 2px; }
    .hint { font-size: 12px; color: var(--text-2); line-height: 1.6; }
    .amount-note { display: flex; gap: 8px; align-items: baseline; font-size: 12px; color: var(--text-2); }
    .amount-note strong { color: var(--text-1); font-size: 13px; }
    .readout {
      display: flex; gap: 12px; flex-wrap: wrap; margin-top: auto; padding: 8px 10px;
      background: var(--input); border-radius: var(--r-sm); font-size: 11.5px; color: var(--text-2);
      font-variant-numeric: tabular-nums;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurveEditorComponent {
  readonly curve = input.required<CurveDefinition>();
  readonly curveChange = output<CurveDefinition>();

  protected readonly modes: SegmentOption<CurveMode>[] = [
    { value: 'amount', label: 'Amount' },
    { value: 'points', label: 'Custom' },
  ];

  protected readonly samples = computed(() => {
    const fn = curveFn(this.curve());
    return [25, 50, 75].map(at => ({ at, out: Math.round(fn(at / 100) * 100) }));
  });

  protected readonly amountNote = computed(() => {
    const amount = this.curve().amount;
    if (amount === 0) return 'straight through, like Linear';
    if (amount === LOG_AMOUNT) return 'the classic Logarithmic taper';
    return amount < 0 ? 'finer control at the top' : 'finer control at the bottom';
  });

  protected patch(change: Partial<CurveDefinition>): void {
    this.curveChange.emit({ ...this.curve(), ...change });
  }

  protected setPoints(points: CurvePoint[]): void {
    this.patch({ points });
  }

  protected setMode(mode: CurveMode): void {
    const current = this.curve();
    const points = mode === 'points' && !current.points?.length ? seedPoints(current) : current.points;
    this.curveChange.emit({ ...current, mode, points });
  }
}
