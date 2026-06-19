import { ChangeDetectionStrategy, Component, computed, ElementRef, input, model, output, viewChild } from '@angular/core';

/**
 * Horizontal value slider (brightness, mapping, animation params). Amber fill +
 * round thumb. Two-way bind [(value)]; `changeEnd` fires once on pointer release
 * for debounce-free committing (e.g. saving brightness).
 */
@Component({
  selector: 'pc-slider',
  standalone: true,
  template: `
    <div #track class="track" [class.disabled]="disabled()" [style.height.px]="height()"
         (pointerdown)="onDown($event)">
      <div class="fill" [style.width.%]="pct()"></div>
      <div class="thumb" [style.left.%]="pct()" [style.width.px]="thumb()" [style.height.px]="thumb()"></div>
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; }
    .track {
      position: relative; width: 100%; border-radius: 4px; background: var(--canvas);
      cursor: pointer; touch-action: none;
    }
    .track.disabled { opacity: .5; pointer-events: none; }
    .fill {
      position: absolute; left: 0; top: 0; bottom: 0; border-radius: 4px;
      background: linear-gradient(90deg, var(--accent-press), var(--accent));
      box-shadow: 0 0 10px var(--accent-glow);
    }
    .thumb {
      position: absolute; top: 50%; transform: translate(-50%, -50%);
      border-radius: 50%; background: #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.5);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SliderComponent {
  readonly value = model<number>(0);
  readonly min = input<number>(0);
  readonly max = input<number>(100);
  readonly step = input<number>(1);
  readonly disabled = input<boolean>(false);
  readonly height = input<number>(6);
  readonly thumb = input<number>(15);
  readonly changeEnd = output<number>();

  private readonly track = viewChild.required<ElementRef<HTMLElement>>('track');

  readonly pct = computed(() => {
    const span = this.max() - this.min();
    if (span <= 0) return 0;
    return Math.max(0, Math.min(100, ((this.value() - this.min()) / span) * 100));
  });

  onDown(ev: PointerEvent): void {
    if (this.disabled()) return;
    (ev.target as HTMLElement).setPointerCapture?.(ev.pointerId);
    this.apply(ev.clientX);
    const move = (e: PointerEvent) => this.apply(e.clientX);
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      this.changeEnd.emit(this.value());
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }

  private apply(clientX: number): void {
    const rect = this.track().nativeElement.getBoundingClientRect();
    if (rect.width <= 0) return;
    const frac = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    const raw = this.min() + frac * (this.max() - this.min());
    const step = this.step() || 1;
    const snapped = Math.round(raw / step) * step;
    const clamped = Math.max(this.min(), Math.min(this.max(), snapped));
    if (clamped !== this.value()) this.value.set(clamped);
  }
}
