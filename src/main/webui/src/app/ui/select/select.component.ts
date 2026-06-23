import { ChangeDetectionStrategy, ChangeDetectorRef, Component, computed, inject, Injector, input, model, output, signal } from '@angular/core';
import { OverlayModule } from '@angular/cdk/overlay';
import { A11yModule, ActiveDescendantKeyManager, Highlightable } from '@angular/cdk/a11y';
import { IconComponent } from '../icon/icon.component';
import { StatusDotComponent, StatusKind } from '../status-dot/status-dot.component';

export interface SelectOption<T = string> {
  value: T;
  label: string;
  status?: StatusKind;
  badge?: string;       // e.g. "MAIN"
  hint?: string;        // e.g. "auto", "offline"
  disabled?: boolean;
  font?: string;        // render this option's label in the given font-family (e.g. a font picker)
}

let nextOptionId = 0;

/** Wraps an option for CDK's ActiveDescendantKeyManager (which drives ↑/↓/Home/End, wrap and
 *  disabled-skip). The manager toggles {@link active} on the highlighted item; the template renders it. */
class ManagedOption<T> implements Highlightable {
  readonly id = `pc-opt-${nextOptionId++}`;
  active = false;
  constructor(readonly opt: SelectOption<T>) {}
  get disabled(): boolean { return !!this.opt.disabled; }
  getLabel(): string { return this.opt.label; }
  setActiveStyles(): void { this.active = true; }
  setInactiveStyles(): void { this.active = false; }
}

/** Dropdown select built on the CDK overlay. Keyboard navigation is handled by CDK's
 *  ActiveDescendantKeyManager (↑/↓ move the highlight, Home/End jump, Enter picks, Esc closes; closed ↑/↓
 *  step the value like a native select). Lists over 10 options get a filter field (auto-focused on open). */
@Component({
  selector: 'pc-select',
  standalone: true,
  imports: [OverlayModule, A11yModule, IconComponent, StatusDotComponent],
  template: `
    <button type="button" class="trigger" [class.block]="block()" cdkOverlayOrigin #trigger="cdkOverlayOrigin"
            role="combobox" [attr.aria-expanded]="open()" (click)="toggle()" (keydown)="onKey($event)">
      @if (microLabel()) { <span class="micro-label">{{ microLabel() }}</span> }
      <span class="val" [style.font-family]="selectedFont()">{{ selectedLabel() }}</span>
      <pc-icon name="chevron-down" [size]="13" [strokeWidth]="2.5"></pc-icon>
    </button>

    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="trigger" [cdkConnectedOverlayOpen]="open()"
                 [cdkConnectedOverlayHasBackdrop]="true" cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                 [cdkConnectedOverlayWidth]="panelWidth()" [cdkConnectedOverlayOffsetY]="6"
                 (backdropClick)="open.set(false)" (detach)="open.set(false)">
      <div class="pc-select-panel panel" cdkTrapFocus [cdkTrapFocusAutoCapture]="showFilter()">
        @if (showFilter()) {
          <input class="filter" type="text" [value]="filter()" placeholder="Filter…" cdkFocusInitial
                 role="combobox" aria-controls="pc-select-list" [attr.aria-activedescendant]="keyManager.activeItem?.id"
                 (input)="setFilter($event)" (keydown)="onKey($event)" />
        }
        <div class="opts" id="pc-select-list" role="listbox">
          @for (item of items(); track item.id; let i = $index) {
            <button type="button" class="opt" role="option" [attr.id]="item.id"
                    [class.selected]="item.opt.value === value()" [class.active]="item.active"
                    [attr.aria-selected]="item.opt.value === value()" [disabled]="item.disabled"
                    (click)="pick(item.opt)" (mouseenter)="setActive(i)">
              @if (item.opt.status) { <pc-status-dot [kind]="item.opt.status" [size]="7"></pc-status-dot> }
              <span class="opt-label" [style.font-family]="item.opt.font">{{ item.opt.label }}</span>
              @if (item.opt.hint) { <span class="hint">{{ item.opt.hint }}</span> }
              @if (item.opt.badge) { <span class="badge">{{ item.opt.badge }}</span> }
            </button>
          }
          @if (items().length === 0) {
            <div class="empty">No matches</div>
          }
        </div>
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
    .trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
    .micro-label { font-family: var(--font-mono); font-size: 10px; color: var(--text-3); letter-spacing: .04em; }
    .val { flex: 1; text-align: left; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    pc-icon { color: var(--text-2); }
    .panel {
      background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-lg);
      padding: 6px; box-shadow: var(--sh-pop); min-width: 160px; display: flex; flex-direction: column;
    }
    .filter {
      margin: 2px 2px 6px; padding: 8px 10px; border-radius: 7px; border: 1px solid var(--raised-line);
      background: var(--panel); color: var(--text-1); font-family: var(--font-ui); font-size: 13px; outline: none;
    }
    .filter:focus { border-color: var(--accent); }
    .opts { max-height: 280px; overflow-y: auto; display: flex; flex-direction: column; }
    .opt {
      display: flex; align-items: center; gap: 9px; width: 100%; text-align: left;
      border: none; background: transparent; color: var(--text-soft); cursor: pointer;
      font-family: var(--font-ui); font-size: 13px; padding: 9px 11px; border-radius: 7px;
    }
    .opt.active:not(:disabled) { background: var(--panel); }
    .opt.selected { background: var(--accent-tint); color: var(--accent-text); }
    .opt:disabled { opacity: .4; cursor: not-allowed; }
    .opt-label { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .hint { font-family: var(--font-mono); font-size: 9.5px; color: var(--text-3); }
    .empty { padding: 10px 11px; color: var(--text-3); font-size: 12.5px; }
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
  readonly filter = signal('');

  private readonly cdr = inject(ChangeDetectorRef);
  private readonly injector = inject(Injector);

  /** Show the filter field for long lists. */
  readonly showFilter = computed(() => this.options().length > 10);

  /** One stable wrapper per option, reused across filtering so the key manager can preserve the active
   *  item by reference when the visible list shrinks/grows. Recreated only when the options input changes. */
  private readonly allItems = computed<ManagedOption<T>[]>(() => this.options().map(o => new ManagedOption(o)));

  readonly items = computed<ManagedOption<T>[]>(() => {
    const q = this.filter().trim().toLowerCase();
    const all = this.allItems();
    return q ? all.filter(i => i.opt.label.toLowerCase().includes(q)) : all;
  });

  /** CDK drives ↑/↓/Home/End, wrap and disabled-skip. The signal overload makes it track {@link items}
   *  itself (its own internal effect re-syncs the active item) — no manual rebuild on list changes. */
  protected readonly keyManager = new ActiveDescendantKeyManager<ManagedOption<T>>(this.items, this.injector)
    .withWrap().withHomeAndEnd().skipPredicate(i => i.disabled);

  readonly selectedLabel = computed(() => this.options().find(o => o.value === this.value())?.label ?? this.placeholder());
  readonly selectedFont = computed(() => this.options().find(o => o.value === this.value())?.font);

  toggle(): void {
    this.open() ? this.open.set(false) : this.openPanel();
  }

  private openPanel(): void {
    this.filter.set('');
    this.open.set(true);
    // Highlight the currently-selected option (or the first enabled one).
    const sel = this.items().find(i => i.opt.value === this.value());
    sel ? this.keyManager.setActiveItem(sel) : this.keyManager.setFirstItemActive();
    this.cdr.markForCheck();
  }

  setFilter(e: Event): void {
    this.filter.set((e.target as HTMLInputElement).value);
  }

  setActive(i: number): void {
    this.keyManager.setActiveItem(i);
    this.cdr.markForCheck();
  }

  onKey(e: KeyboardEvent): void {
    if (!this.open()) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); this.cycleValue(e.key === 'ArrowDown' ? 1 : -1); }
      else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); this.openPanel(); }
      return;
    }
    if (e.key === 'Enter') {
      e.preventDefault();
      const active = this.keyManager.activeItem;
      if (active) this.pick(active.opt);
      return;
    }
    if (e.key === 'Escape') { e.preventDefault(); this.open.set(false); return; }
    // ↑/↓/Home/End — let the CDK manager drive (it preventDefaults the keys it handles).
    this.keyManager.onKeydown(e);
    this.cdr.markForCheck();
    queueMicrotask(() => document.querySelector('.pc-select-panel .opt.active')?.scrollIntoView({ block: 'nearest' }));
  }

  /** Closed-state ↑/↓: step the selected value through the enabled options (like a native select). */
  private cycleValue(delta: number): void {
    const opts = this.options().filter(o => !o.disabled);
    if (!opts.length) return;
    const cur = opts.findIndex(o => o.value === this.value());
    const next = cur < 0 ? (delta > 0 ? 0 : opts.length - 1) : clamp(cur + delta, 0, opts.length - 1);
    this.value.set(opts[next].value);
  }

  pick(opt: SelectOption<T>): void {
    if (opt.disabled) return;
    this.value.set(opt.value);
    this.open.set(false);
  }
}

function clamp(n: number, lo: number, hi: number): number {
  return Math.min(Math.max(n, lo), hi);
}
