import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { StatusDotComponent } from '../status-dot/status-dot.component';

export type ConnState = 'connected' | 'connecting' | 'reconnecting' | 'error';

/** Pill connection-status badge (backend connection + per-integration status). */
@Component({
  selector: 'pc-connection-badge',
  standalone: true,
  imports: [StatusDotComponent],
  template: `
    <span class="badge" [attr.data-state]="state()">
      @if (state() === 'connecting') {
        <span class="mini-spin"></span>
      } @else {
        <pc-status-dot [kind]="dotKind()" [size]="7"></pc-status-dot>
      }
      {{ label() || defaultLabel() }}
    </span>
  `,
  styles: [`
    :host { display: inline-flex; }
    .badge {
      display: inline-flex; align-items: center; gap: 7px;
      border-radius: var(--r-pill); padding: 6px 13px; font-size: 12.5px;
      border: 1px solid var(--line); cursor: pointer;
    }
    .badge[data-state="connected"] { background: rgba(67,192,138,0.1); border-color: rgba(67,192,138,0.35); color: var(--ok-text); }
    .badge[data-state="connecting"],
    .badge[data-state="reconnecting"] { background: rgba(255,176,32,0.1); border-color: rgba(255,176,32,0.35); color: var(--warn-text); }
    .badge[data-state="error"] { background: rgba(242,82,104,0.1); border-color: rgba(242,82,104,0.35); color: var(--err-text); }
    .mini-spin {
      width: 11px; height: 11px; border-radius: 50%; border: 2px solid rgba(255,176,32,0.3);
      border-top-color: var(--accent); animation: pcp-spin .8s linear infinite;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConnectionBadgeComponent {
  readonly state = input.required<ConnState>();
  readonly label = input<string>('');

  readonly dotKind = computed(() => {
    switch (this.state()) {
      case 'connected': return 'ok' as const;
      case 'reconnecting': return 'reconnecting' as const;
      case 'error': return 'error' as const;
      default: return 'warn' as const;
    }
  });

  readonly defaultLabel = computed(() => {
    switch (this.state()) {
      case 'connected': return 'Connected';
      case 'connecting': return 'Connecting';
      case 'reconnecting': return 'Reconnecting';
      case 'error': return 'Error';
    }
  });
}
