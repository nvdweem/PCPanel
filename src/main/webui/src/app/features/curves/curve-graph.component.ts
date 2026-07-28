import { ChangeDetectionStrategy, Component, computed, ElementRef, input, output, viewChild } from '@angular/core';
import { CurveDefinition, CurvePoint } from '../../models/generated/backend.types';
import { amountCurve, curveFn, curvePath, LOG_AMOUNT } from './curve.util';

/** Breathing room around the plot, as a share of the box — a fixed inset would swallow a thumbnail. */
const PAD_RATIO = 0.085;
/** Below this the graph is a glyph in a list row, where grid and reference lines are only noise. */
const DETAIL_FROM = 110;

/**
 * Plots a curve: dial position across, resulting output up. Linear and the logarithmic taper are drawn
 * faintly behind it as a reference for how far a shape has been pushed.
 *
 * When `interactive` is set the control points can be dragged, added by clicking the plot and removed by
 * right-clicking; the end points slide vertically only, so the curve always spans the full throw.
 */
@Component({
  selector: 'pc-curve-graph',
  standalone: true,
  template: `
    <svg #plot [attr.viewBox]="'0 0 ' + size() + ' ' + size()" [style.width.px]="size()"
         [class.interactive]="interactive()" (pointerdown)="onDown($event)"
         (pointermove)="onMove($event)" (pointerup)="stopDrag()" (pointerleave)="stopDrag()"
         (contextmenu)="onContext($event)">
      @if (detailed()) {
        @for (t of ticks; track t) {
          <line class="grid" [attr.x1]="px(t)" [attr.y1]="py(0)" [attr.x2]="px(t)" [attr.y2]="py(1)"></line>
          <line class="grid" [attr.x1]="px(0)" [attr.y1]="py(t)" [attr.x2]="px(1)" [attr.y2]="py(t)"></line>
        }
        <path class="ghost" [attr.d]="linearPath()"></path>
        <path class="ghost" [attr.d]="logPath()"></path>
      }
      <path class="curve" [attr.d]="path()"></path>
      @if (interactive()) {
        @for (p of points(); track $index) {
          <circle class="pt" [attr.cx]="px(p.x)" [attr.cy]="py(p.y)" r="6" [attr.data-i]="$index"></circle>
        }
      }
    </svg>
  `,
  styles: [`
    :host { display: block; }
    svg { display: block; touch-action: none; }
    svg.interactive { cursor: crosshair; }
    .grid { stroke: var(--line); stroke-opacity: .6; }
    .ghost { fill: none; stroke: var(--text-2); stroke-opacity: .35; stroke-width: 1.25; stroke-dasharray: 4 4; }
    .curve { fill: none; stroke: var(--accent); stroke-width: 2.5; }
    .pt { fill: var(--accent); stroke: var(--bg-1, #fff); stroke-width: 2; cursor: grab; }
    .pt:active { cursor: grabbing; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurveGraphComponent {
  readonly curve = input.required<CurveDefinition>();
  readonly size = input<number>(260);
  readonly interactive = input<boolean>(false);
  readonly pointsChange = output<CurvePoint[]>();

  protected readonly ticks = [0.25, 0.5, 0.75];
  private readonly plot = viewChild.required<ElementRef<SVGSVGElement>>('plot');
  private dragging: number | null = null;

  protected readonly detailed = computed(() => this.size() >= DETAIL_FROM);
  private readonly pad = computed(() => this.size() * PAD_RATIO);

  protected readonly points = computed(() => this.curve().points ?? []);
  protected readonly path = computed(() => curvePath(curveFn(this.curve()), this.size(), this.pad()));
  protected readonly linearPath = computed(() => curvePath(amountCurve(0), this.size(), this.pad()));
  protected readonly logPath = computed(() => curvePath(amountCurve(LOG_AMOUNT), this.size(), this.pad()));

  protected px(x: number): number { return this.pad() + x * (this.size() - 2 * this.pad()); }
  protected py(y: number): number {
    return this.size() - this.pad() - Math.min(1, Math.max(0, y)) * (this.size() - 2 * this.pad());
  }

  protected onDown(ev: PointerEvent): void {
    if (!this.interactive() || ev.button === 2) return;
    const index = this.indexOf(ev.target);
    if (index !== null) {
      this.dragging = index;
      this.plot().nativeElement.setPointerCapture(ev.pointerId);
      ev.preventDefault();
      return;
    }
    const { x, y } = this.toUnit(ev);
    this.pointsChange.emit([...this.points(), { x, y }].sort((a, b) => a.x - b.x));
  }

  protected onMove(ev: PointerEvent): void {
    if (this.dragging === null) return;
    const points = this.points();
    const i = this.dragging;
    const { x, y } = this.toUnit(ev);
    const isEnd = i === 0 || i === points.length - 1;
    const low = i === 0 ? 0 : points[i - 1].x + 0.01;
    const high = i === points.length - 1 ? 1 : points[i + 1].x - 0.01;
    const next = [...points];
    next[i] = { x: isEnd ? points[i].x : Math.min(high, Math.max(low, x)), y };
    this.pointsChange.emit(next);
  }

  protected stopDrag(): void { this.dragging = null; }

  protected onContext(ev: MouseEvent): void {
    if (!this.interactive()) return;
    const index = this.indexOf(ev.target);
    const points = this.points();
    if (index === null) return;
    ev.preventDefault();
    if (index === 0 || index === points.length - 1 || points.length <= 2) return;
    this.pointsChange.emit(points.filter((_, i) => i !== index));
  }

  private indexOf(target: EventTarget | null): number | null {
    const attr = (target as Element | null)?.getAttribute?.('data-i');
    return attr === null || attr === undefined ? null : +attr;
  }

  private toUnit(ev: { clientX: number; clientY: number }): CurvePoint {
    const rect = this.plot().nativeElement.getBoundingClientRect();
    const scale = this.size() / rect.width;
    const pad = this.pad();
    const span = this.size() - 2 * pad;
    const x = ((ev.clientX - rect.left) * scale - pad) / span;
    const y = (this.size() - pad - (ev.clientY - rect.top) * scale) / span;
    return { x: Math.min(1, Math.max(0, x)), y: Math.min(1, Math.max(0, y)) };
  }
}
