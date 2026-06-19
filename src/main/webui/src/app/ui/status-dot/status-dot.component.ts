import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type StatusKind = 'ok' | 'warn' | 'error' | 'idle' | 'connecting' | 'reconnecting';

/** Small status dot used in device rows, integration lists and settings tabs. */
@Component({
  selector: 'pc-status-dot',
  standalone: true,
  template: `
    @if (kind() === 'connecting' || kind() === 'reconnecting') {
      <span class="dot spin" [style.width.px]="size() + 4" [style.height.px]="size() + 4"></span>
    } @else {
      <span class="dot" [class.glow]="glow()"
            [style.width.px]="size()" [style.height.px]="size()"
            [style.background]="color()" [style.box-shadow]="glow() ? '0 0 6px ' + color() : 'none'"></span>
    }
  `,
  styles: [`
    :host { display: inline-flex; align-items: center; }
    .dot { border-radius: 50%; flex: none; }
    .spin {
      border-radius: 50%; border: 2px solid rgba(255,176,32,0.3);
      border-top-color: var(--accent); animation: pcp-spin .8s linear infinite;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusDotComponent {
  readonly kind = input<StatusKind>('idle');
  readonly size = input<number>(7);
  readonly glow = input<boolean>(true);

  readonly color = computed(() => {
    switch (this.kind()) {
      case 'ok': return 'var(--ok)';
      case 'warn': return 'var(--warn)';
      case 'error': return 'var(--err)';
      default: return 'var(--idle)';
    }
  });
}
