import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { StatusDotComponent } from '../status-dot/status-dot.component';
import { ToastService } from './toast.service';

/** Fixed bottom-right stack of toasts. Mount once near the app root. */
@Component({
  selector: 'pc-toast-host',
  standalone: true,
  imports: [IconComponent, StatusDotComponent],
  template: `
    <div class="host">
      @for (t of toasts.toasts(); track t.id) {
        <div class="toast" [attr.data-kind]="t.kind">
          <pc-status-dot [kind]="dot(t.kind)" [size]="8"></pc-status-dot>
          <div class="body">
            <div class="msg">{{ t.message }}</div>
            @if (t.sub) { <div class="sub">{{ t.sub }}</div> }
          </div>
          <button type="button" class="close" (click)="toasts.dismiss(t.id)">
            <pc-icon name="x" [size]="13"></pc-icon>
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .host { position: fixed; right: 22px; bottom: 22px; z-index: 1400; display: flex; flex-direction: column; gap: 10px; pointer-events: none; }
    .toast {
      pointer-events: auto; display: flex; align-items: center; gap: 11px; min-width: 260px; max-width: 380px;
      background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-lg);
      padding: 11px 14px; box-shadow: var(--sh-menu); animation: pcp-toast-in .22s ease;
    }
    .toast[data-kind="success"] { background: rgba(59,224,106,0.08); border-color: rgba(59,224,106,0.3); }
    .toast[data-kind="error"] { background: rgba(242,82,104,0.08); border-color: rgba(242,82,104,0.3); }
    .toast[data-kind="warn"] { background: rgba(255,176,32,0.08); border-color: rgba(255,176,32,0.3); }
    .body { flex: 1; min-width: 0; }
    .msg { font-size: 13.5px; color: var(--text-1); }
    .sub { font-size: 11.5px; color: var(--text-2); margin-top: 2px; }
    .close { border: none; background: transparent; color: var(--text-3); cursor: pointer; display: flex; padding: 2px; }
    .close:hover { color: var(--text-1); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastHostComponent {
  readonly toasts = inject(ToastService);
  dot(kind: string): 'ok' | 'warn' | 'error' | 'idle' {
    return kind === 'success' ? 'ok' : kind === 'warn' ? 'warn' : kind === 'error' ? 'error' : 'idle';
  }
}
