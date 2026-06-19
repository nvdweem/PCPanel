import { ChangeDetectionStrategy, Component, computed, input, model, output, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

export interface PickerItem {
  key: string;
  label: string;
  icon?: string | null;   // data-URI image (real process/device icon)
  sub?: string;           // right-aligned tag, e.g. OUTPUT / INPUT
}

/**
 * Searchable app / audio-device picker. Decoupled from data sources: feed it
 * `items`; it handles filtering, single/multi selection and the icon tiles.
 * Real icons render when provided; otherwise a colored letter tile is shown.
 */
@Component({
  selector: 'pc-app-picker',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="picker">
      <div class="head">
        <pc-icon name="search" [size]="15"></pc-icon>
        <input class="q" [value]="query()" (input)="query.set($any($event.target).value)"
               [placeholder]="placeholder()" spellcheck="false">
        @if (multi() && value().length) {
          <span class="count">{{ value().length }} selected</span>
        }
      </div>
      <div class="list">
        @for (it of filtered(); track it.key) {
          <button type="button" class="row" [class.sel]="isSel(it.key)" (click)="toggle(it.key)">
            <span class="tile" [style.background]="tileBg(it)">
              @if (it.icon) { <img [src]="it.icon" alt=""> } @else { {{ letter(it.label) }} }
            </span>
            <span class="name">{{ it.label }}</span>
            @if (it.sub) { <span class="sub">{{ it.sub }}</span> }
            @if (multi()) {
              <span class="check" [class.on]="isSel(it.key)">
                @if (isSel(it.key)) { <pc-icon name="check" [size]="11" [strokeWidth]="3.5"></pc-icon> }
              </span>
            } @else if (isSel(it.key)) {
              <pc-icon name="check" [size]="14" [strokeWidth]="3"></pc-icon>
            }
          </button>
        }
        @if (!filtered().length) {
          <div class="empty">{{ emptyText() }}</div>
        }
      </div>
    </div>
  `,
  styles: [`
    .picker { background: var(--input); border: 1px solid var(--line); border-radius: var(--r-lg); overflow: hidden; }
    .head { display: flex; align-items: center; gap: 9px; padding: 11px 13px; border-bottom: 1px solid var(--line-hair); }
    .head pc-icon { color: var(--text-3); }
    .q { flex: 1; min-width: 0; background: transparent; border: none; outline: none; color: var(--text-1); font-size: 13px; }
    .q::placeholder { color: var(--text-3); }
    .count { font-family: var(--font-mono); font-size: 10px; color: var(--accent); background: var(--accent-tint); padding: 3px 8px; border-radius: var(--r-pill); }
    .list { max-height: 260px; overflow-y: auto; }
    .row { display: flex; align-items: center; gap: 11px; width: 100%; text-align: left; border: none; background: transparent; color: var(--text-1); cursor: pointer; padding: 10px 13px; }
    .row:hover { background: rgba(255,255,255,0.02); }
    .row.sel { background: var(--accent-tint-soft); }
    .tile { width: 28px; height: 28px; border-radius: 7px; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 12px; color: #fff; flex: none; overflow: hidden; }
    .tile img { width: 100%; height: 100%; object-fit: contain; }
    .name { font-size: 13.5px; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .sub { font-family: var(--font-mono); font-size: 9.5px; color: var(--text-3); }
    .check { width: 18px; height: 18px; border-radius: 5px; border: 1.5px solid var(--line-2); display: flex; align-items: center; justify-content: center; color: var(--accent-ink); }
    .check.on { background: var(--accent); border-color: var(--accent); }
    .row pc-icon { color: var(--accent); }
    .empty { padding: 18px; text-align: center; color: var(--text-3); font-size: 12.5px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppPickerComponent {
  readonly items = input.required<PickerItem[]>();
  readonly multi = input<boolean>(false);
  readonly value = model<string[]>([]);
  readonly placeholder = input<string>('Search apps & devices…');
  readonly emptyText = input<string>('Nothing found');
  /** Fires after a single-select pick (consumer can close a popover). */
  readonly picked = output<string>();

  readonly query = signal('');

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const list = this.items();
    if (!q) return list;
    return list.filter(i => i.label.toLowerCase().includes(q) || i.key.toLowerCase().includes(q));
  });

  isSel(key: string): boolean { return this.value().includes(key); }

  toggle(key: string): void {
    if (this.multi()) {
      const cur = this.value();
      this.value.set(cur.includes(key) ? cur.filter(k => k !== key) : [...cur, key]);
    } else {
      this.value.set([key]);
      this.picked.emit(key);
    }
  }

  letter(label: string): string { return (label.trim()[0] ?? '?').toUpperCase(); }

  tileBg(it: PickerItem): string {
    if (it.icon) return 'var(--raised)';
    // deterministic hue from the label so tiles look stable across renders
    let h = 0;
    for (const ch of it.label) h = (h * 31 + ch.charCodeAt(0)) % 360;
    return `linear-gradient(135deg, hsl(${h},55%,52%), hsl(${(h + 28) % 360},55%,38%))`;
  }
}
