import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { CommandCategory, CommandDef, CommandKind, COMMANDS, categoryLabel } from './command-catalog';
import { IntegrationDataService } from './integration-data.service';
import { PlatformService } from '../../services/platform.service';
import { IconComponent, StatusDotComponent } from '../../ui';

interface MenuRow { def: CommandDef; status?: 'ok' | 'idle' | 'connecting'; offline: boolean; unsupported?: boolean; }

/**
 * The single categorized + filterable command picker, shared by every place that lets the user choose
 * a command (the control page's "Add action" and the stepped-switch band editor). It owns the filter
 * box, the category grouping, and the per-integration live status dots / offline tags, and emits the
 * chosen {@link CommandDef}. Reusing it keeps those affordances (and their behaviour) in one place.
 */
@Component({
  selector: 'pc-command-picker',
  standalone: true,
  imports: [IconComponent, StatusDotComponent],
  template: `
    <button class="pc-btn primary trigger" (click)="open.set(!open())">
      <pc-icon name="plus" [size]="16" [strokeWidth]="2.4"></pc-icon> {{ triggerLabel() }}
    </button>
    @if (open()) {
      <div class="backdrop" (click)="open.set(false)"></div>
      <div class="menu">
        <div class="filter">
          <pc-icon name="search" [size]="13"></pc-icon>
          <input class="filter-in" placeholder="Filter…" [value]="query()" (input)="query.set($any($event.target).value)" autofocus>
        </div>
        @for (g of menuGroups(); track g.label) {
          <div class="grp-label">{{ g.label }}</div>
          @for (row of g.rows; track row.def.type) {
            <button class="row" [class.offline]="row.offline || row.unsupported" [disabled]="row.unsupported" (click)="choose(row.def)">
              @if (row.status) { <pc-status-dot [kind]="row.status" [size]="6"></pc-status-dot> }
              <span>{{ row.def.label }}</span>
              @if (row.unsupported) { <span class="off-tag">unavailable</span> }
              @else if (row.offline) { <span class="off-tag">offline</span> }
            </button>
          }
        }
      </div>
    }
  `,
  styles: [`
    :host { display: block; position: relative; }
    .trigger { width: 100%; padding: 11px; }
    .backdrop { position: fixed; inset: 0; z-index: 40; }
    .menu { position: absolute; top: calc(100% + 8px); left: 0; right: 0; z-index: 50; background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-lg); padding: 8px; box-shadow: var(--sh-pop); max-height: 360px; overflow: auto; }
    .filter { display: flex; align-items: center; gap: 8px; background: var(--input); border: 1px solid var(--line); border-radius: 8px; padding: 8px 11px; margin-bottom: 6px; color: var(--text-3); }
    .filter-in { flex: 1; min-width: 0; background: transparent; border: none; outline: none; color: var(--text-1); font-size: 12px; }
    .grp-label { font-family: var(--font-mono); font-size: 9px; letter-spacing: 0.12em; color: var(--text-3); padding: 8px 9px 3px; }
    .row { display: flex; align-items: center; gap: 7px; width: 100%; text-align: left; border: none; background: transparent; color: var(--text-soft); font-size: 12.5px; padding: 7px 9px; border-radius: var(--r-sm); cursor: pointer; }
    .row:hover { background: var(--accent-tint-soft); color: var(--accent-text); }
    .row.offline { color: var(--text-3); }
    .row:disabled { cursor: not-allowed; }
    .row:disabled:hover { background: transparent; color: var(--text-3); }
    .off-tag { font-size: 9px; color: var(--text-3); margin-left: auto; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPickerComponent {
  private readonly integrations = inject(IntegrationDataService);
  private readonly platform = inject(PlatformService);

  /** Which control slot the picker is choosing for: 'dial' (rotate) or 'button' (press / band action). */
  readonly kind = input.required<CommandKind>();
  readonly triggerLabel = input<string>('Add action');
  readonly pick = output<CommandDef>();

  readonly query = signal('');
  readonly open = signal(false);

  readonly menuGroups = computed(() => {
    const kind = this.kind();
    const q = this.query().trim().toLowerCase();
    const defs = COMMANDS.filter(d => d.kinds.includes(kind) && (!q || d.label.toLowerCase().includes(q)));
    const cats: CommandCategory[] = ['audio', 'system', 'integration'];
    return cats.map(cat => ({
      label: categoryLabel(cat),
      rows: defs.filter(d => d.category === cat).map(d => this.toMenuRow(d)),
    })).filter(g => g.rows.length);
  });

  choose(def: CommandDef): void {
    this.pick.emit(def);
    this.open.set(false);
    this.query.set('');
  }

  /** Whether a command's integration can run on this host. */
  private platformOk(def: CommandDef): boolean {
    if (def.integration === 'voicemeeter') return this.platform.voicemeeterSupported();
    if (def.integration === 'wavelink') return this.platform.waveLinkSupported();
    return true;
  }

  private toMenuRow(def: CommandDef): MenuRow {
    if (!def.integration) return { def, offline: false };
    if (!this.platformOk(def)) return { def, status: 'idle', offline: false, unsupported: true };
    // Honest status: green only with positive evidence; Voicemeeter has no live signal.
    if (def.integration === 'voicemeeter') return { def, status: 'idle', offline: false };
    const connected = def.integration === 'obs' ? this.integrations.obsConnected()
      : def.integration === 'homeassistant' ? this.integrations.haConnected()
        : this.integrations.waveLinkConnected();
    const loading = def.integration === 'obs' ? this.integrations.obsScenes.isLoading()
      : def.integration === 'homeassistant' ? this.integrations.haStatus.isLoading()
        : this.integrations.waveLink.isLoading();
    if (loading) return { def, status: 'connecting', offline: false };
    return { def, status: connected ? 'ok' : 'idle', offline: !connected };
  }
}
