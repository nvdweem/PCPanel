import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';
import { OverlayModule } from '@angular/cdk/overlay';
import { IconComponent } from '../icon/icon.component';
import { StatusDotComponent, StatusKind } from '../status-dot/status-dot.component';

export interface SelectOption<T = string> {
  value: T;
  label: string;
  status?: StatusKind;
  badge?: string;       // e.g. "MAIN"
  hint?: string;        // e.g. "auto", "offline"
  disabled?: boolean;
}

/** Dropdown select built on the CDK overlay. Optional leading micro-label and footer action. */
@Component({
  selector: 'pc-select',
  standalone: true,
  imports: [OverlayModule, IconComponent, StatusDotComponent],
  template: `
    <button type="button" class="trigger" [class.block]="block()" cdkOverlayOrigin #trigger="cdkOverlayOrigin"
            (click)="open.set(!open())">
      @if (microLabel()) { <span class="micro-label">{{ microLabel() }}</span> }
      <span class="val">{{ selectedLabel() }}</span>
      <pc-icon name="chevron-down" [size]="13" [strokeWidth]="2.5"></pc-icon>
    </button>

    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="trigger" [cdkConnectedOverlayOpen]="open()"
                 [cdkConnectedOverlayHasBackdrop]="true" cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                 [cdkConnectedOverlayWidth]="panelWidth()" [cdkConnectedOverlayOffsetY]="6"
                 (backdropClick)="open.set(false)" (detach)="open.set(false)">
      <div class="panel">
        @for (opt of options(); track opt.value) {
          <button type="button" class="opt" [class.selected]="opt.value === value()" [disabled]="opt.disabled"
                  (click)="pick(opt)">
            @if (opt.status) { <pc-status-dot [kind]="opt.status" [size]="7"></pc-status-dot> }
            <span class="opt-label">{{ opt.label }}</span>
            @if (opt.hint) { <span class="hint">{{ opt.hint }}</span> }
            @if (opt.badge) { <span class="badge">{{ opt.badge }}</span> }
          </button>
        }
        @if (footerLabel()) {
          <div class="divider"></div>
          <button type="button" class="opt footer" (click)="footerAction.emit(); open.set(false)">
            <pc-icon name="plus" [size]="13" [strokeWidth]="2.2"></pc-icon>
            <span class="opt-label">{{ footerLabel() }}</span>
          </button>
        }
      </div>
    </ng-template>
  `,
  styles: [`
    :host { display: inline-flex; }
    .trigger {
      display: inline-flex; align-items: center; gap: 9px; cursor: pointer;
      background: var(--panel); border: 1px solid var(--raised-line); border-radius: var(--r-md);
      padding: 9px 13px; color: var(--text-1); font-family: var(--font-ui); font-size: 13px;
    }
    .trigger.block { display: flex; width: 100%; }
    .trigger:hover { border-color: var(--line-2); }
    .micro-label { font-family: var(--font-mono); font-size: 10px; color: var(--text-3); letter-spacing: .04em; }
    .val { flex: 1; text-align: left; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    pc-icon { color: var(--text-2); }
    .panel {
      background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-lg);
      padding: 6px; box-shadow: var(--sh-pop); min-width: 160px; max-height: 320px; overflow-y: auto;
    }
    .opt {
      display: flex; align-items: center; gap: 9px; width: 100%; text-align: left;
      border: none; background: transparent; color: var(--text-soft); cursor: pointer;
      font-family: var(--font-ui); font-size: 13px; padding: 9px 11px; border-radius: 7px;
    }
    .opt:hover:not(:disabled) { background: var(--panel); }
    .opt.selected { background: var(--accent-tint); color: var(--accent-text); }
    .opt:disabled { opacity: .4; cursor: not-allowed; }
    .opt-label { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .hint { font-family: var(--font-mono); font-size: 9.5px; color: var(--text-3); }
    .badge {
      font-family: var(--font-mono); font-size: 9.5px; color: var(--accent-ink);
      background: var(--accent); padding: 2px 6px; border-radius: var(--r-pill); font-weight: 600;
    }
    .opt.footer { color: var(--accent); }
    .divider { height: 1px; background: var(--line); margin: 4px 8px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectComponent<T = string> {
  readonly options = input.required<SelectOption<T>[]>();
  readonly value = model<T>();
  readonly placeholder = input<string>('Select…');
  readonly microLabel = input<string>('');
  readonly block = input<boolean>(false);
  readonly panelWidth = input<number | string>('auto');
  readonly footerLabel = input<string>('');
  readonly footerAction = output<void>();

  readonly open = model<boolean>(false);

  readonly selectedLabel = computed(() => {
    const v = this.value();
    const found = this.options().find(o => o.value === v);
    return found?.label ?? this.placeholder();
  });

  pick(opt: SelectOption<T>): void {
    if (opt.disabled) return;
    this.value.set(opt.value);
    this.open.set(false);
  }
}
