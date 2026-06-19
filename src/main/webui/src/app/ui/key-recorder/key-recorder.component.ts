import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';

const MODS = ['Control', 'Shift', 'Alt', 'Meta'];
const MOD_LABEL: Record<string, string> = { Control: 'Ctrl', Shift: 'Shift', Alt: 'Alt', Meta: 'Win' };

/**
 * Key-combo recorder. Two-way binds [(value)] a "+"-joined combo string
 * (e.g. "Ctrl+Shift+M"). Click to record; press the keys; it captures modifiers
 * plus the final key.
 */
@Component({
  selector: 'pc-key-recorder',
  standalone: true,
  template: `
    @if (!recording()) {
      <button type="button" class="field" (click)="start()">
        @if (keys().length) {
          @for (k of keys(); track $index; let last = $last) {
            <span class="key">{{ k }}</span>
            @if (!last) { <span class="plus">+</span> }
          }
        } @else {
          <span class="empty">Click to set a shortcut…</span>
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
    .plus { color: var(--text-3); }
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

  readonly keys = computed(() => this.value() ? this.value().split('+').map(s => s.trim()).filter(Boolean) : []);

  start(): void { this.recording.set(true); }
  stop(): void { this.recording.set(false); }

  onKey(ev: KeyboardEvent): void {
    ev.preventDefault();
    ev.stopPropagation();
    if (ev.key === 'Escape') { this.stop(); return; }

    const parts: string[] = [];
    if (ev.ctrlKey) parts.push(MOD_LABEL['Control']);
    if (ev.shiftKey) parts.push(MOD_LABEL['Shift']);
    if (ev.altKey) parts.push(MOD_LABEL['Alt']);
    if (ev.metaKey) parts.push(MOD_LABEL['Meta']);

    // Wait for a non-modifier key to finalize the combo.
    if (MODS.includes(ev.key)) return;

    const main = ev.key.length === 1 ? ev.key.toUpperCase() : ev.key;
    parts.push(main);
    this.value.set(parts.join('+'));
    this.stop();
  }
}
