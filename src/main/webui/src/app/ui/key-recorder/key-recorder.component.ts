import { ChangeDetectionStrategy, Component, computed, model, signal } from '@angular/core';

import { formatCombo, MOD_ORDER, parseCombo } from './key-combo';

const MODS = ['Control', 'Shift', 'Alt', 'Meta'];
const MOD_LABEL: Record<string, string> = { Control: 'Ctrl', Shift: 'Shift', Alt: 'Alt', Meta: 'Win' };
/** Keys whose character is unusable in a "+"-joined combo, so they are recorded by name. */
const KEY_LABEL: Record<string, string> = { ' ': 'Space', '+': 'Plus' };

/**
 * Key-combo recorder. Two-way binds [(value)] a "+"-joined combo string
 * (e.g. "Ctrl+Shift+M"). Click to record; press the keys; it captures modifiers
 * plus the final key.
 *
 * <p>The modifiers are also toggle buttons. The OS and the browser claim some combos before the page
 * sees them — Windows takes every Win chord, the browser takes Alt+Left for history — so those are
 * built by recording the plain key and switching the modifiers on.
 */
@Component({
  selector: 'pc-key-recorder',
  standalone: true,
  template: `
    <div class="mods">
      @for (m of modKeys; track m) {
        <button type="button" class="mod" [class.on]="hasMod(m)" (click)="toggleMod(m)">{{ m }}</button>
      }
    </div>
    @if (!recording()) {
      <button type="button" class="field" (click)="start()">
        @if (mainKey(); as k) {
          <span class="key">{{ k }}</span>
        } @else {
          <span class="empty">Click to set a key…</span>
        }
        <span class="spacer"></span>
        <span class="set">SET</span>
      </button>
    } @else {
      <button type="button" class="field recording" (keydown)="onKey($event)" (blur)="stop()" autofocus>
        <span class="rec-dot"></span>
        <span class="rec-text">Recording… press the keys now</span>
      </button>
    }
  `,
  styles: [`
    :host { display: block; }
    .mods { display: flex; gap: 6px; margin-bottom: 8px; }
    .mod {
      font-family: var(--font-mono); font-size: 12px; color: var(--text-3);
      background: var(--input); border: 1px solid var(--raised-line);
      border-radius: var(--r-sm); padding: 5px 10px; cursor: pointer;
    }
    .mod:hover { color: var(--text-1); }
    .mod.on {
      color: var(--accent-text); background: var(--accent-tint);
      border-color: var(--accent-border-2, var(--accent));
    }
    .field {
      display: flex; align-items: center; gap: 9px; width: 100%; text-align: left;
      background: var(--input); border: 1px solid var(--raised-line); border-radius: var(--r-md);
      padding: 12px 14px; cursor: pointer; color: var(--text-1); font-family: var(--font-ui);
    }
    .key {
      font-family: var(--font-mono); font-size: 13px; color: var(--text-1);
      background: var(--raised); border: 1px solid var(--line-2); border-bottom-width: 2px;
      border-radius: var(--r-sm); padding: 4px 9px;
    }
    .empty { color: var(--text-3); font-size: 13px; }
    .spacer { flex: 1; }
    .set { font-family: var(--font-mono); font-size: 11px; color: var(--text-3); }
    .field.recording { background: rgba(242,82,104,0.08); border-color: rgba(242,82,104,0.4); cursor: default; }
    .rec-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--err); animation: pcp-blink 1s steps(1) infinite; }
    .rec-text { font-size: 13px; color: #FF9AA8; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KeyRecorderComponent {
  readonly value = model<string>('');
  readonly recording = signal(false);
  readonly modKeys = MOD_ORDER;

  private readonly combo = computed(() => parseCombo(this.value()));

  /** The modifiers the combo carries, however it spells them. */
  readonly mods = computed(() => this.combo().mods);
  /** The key the combo ends on, empty when only modifiers are set so far. */
  readonly mainKey = computed(() => this.combo().key);

  start(): void { this.recording.set(true); }
  stop(): void { this.recording.set(false); }

  hasMod(mod: string): boolean { return this.mods().includes(mod); }

  toggleMod(mod: string): void {
    const next = this.hasMod(mod) ? this.mods().filter(m => m !== mod) : [...this.mods(), mod];
    this.emit(next, this.mainKey());
  }

  onKey(ev: KeyboardEvent): void {
    ev.preventDefault();
    ev.stopPropagation();
    if (ev.key === 'Escape') { this.stop(); return; }

    const mods: string[] = [];
    if (ev.ctrlKey) mods.push(MOD_LABEL['Control']);
    if (ev.shiftKey) mods.push(MOD_LABEL['Shift']);
    if (ev.altKey) mods.push(MOD_LABEL['Alt']);
    if (ev.metaKey) mods.push(MOD_LABEL['Meta']);

    // Wait for a non-modifier key to finalize the combo.
    if (MODS.includes(ev.key)) return;

    const main = KEY_LABEL[ev.key] ?? (ev.key.length === 1 ? ev.key.toUpperCase() : ev.key);
    this.emit(mods, main);
    this.stop();
  }

  private emit(mods: string[], key: string): void {
    this.value.set(formatCombo(mods, key));
  }
}
