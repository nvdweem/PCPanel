import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { OverlayModule } from '@angular/cdk/overlay';
import { A11yModule } from '@angular/cdk/a11y';
import { CommandCategory, CommandDef, CommandKind, COMMANDS, categoryLabel, Integration } from './command-catalog';
import { IntegrationDataService } from './integration-data.service';
import { PlatformService } from '../../services/platform.service';
import { IconComponent, StatusDotComponent } from '../../ui';

type Status = 'ok' | 'idle' | 'connecting';
interface PickerGroup { label: string; status?: Status; statusText?: string; offline: boolean; unsupported?: boolean; rows: CommandDef[]; }

/**
 * The single filterable command picker, shared by every place that lets the user choose a command (the
 * control page's "Add action" and the stepped-switch band editor). Generic commands are grouped by
 * category (Audio, Device & System); integration commands are grouped by integration (OBS, Voicemeeter,
 * Wave Link, Home Assistant) with the live connection status shown once on the group header. Emits the
 * chosen {@link CommandDef}. Reusing it keeps those affordances (and their behaviour) in one place.
 */
@Component({
  selector: 'pc-command-picker',
  standalone: true,
  imports: [OverlayModule, A11yModule, IconComponent, StatusDotComponent],
  template: `
    <button #trigBtn class="pc-btn trigger" [class.primary]="variant() === 'primary'" [class.subtle]="variant() === 'subtle'"
            cdkOverlayOrigin #trig="cdkOverlayOrigin" (click)="toggle(trigBtn)">
      <pc-icon name="plus" [size]="variant() === 'subtle' ? 13 : 16" [strokeWidth]="2.4"></pc-icon> {{ triggerLabel() }}
    </button>
    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="trig" [cdkConnectedOverlayOpen]="open()"
                 [cdkConnectedOverlayHasBackdrop]="true" cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                 [cdkConnectedOverlayWidth]="menuWidth()" [cdkConnectedOverlayOffsetY]="6"
                 (backdropClick)="open.set(false)" (detach)="open.set(false)">
      <div class="menu" cdkTrapFocus [cdkTrapFocusAutoCapture]="true">
        <div class="filter">
          <pc-icon name="search" [size]="13"></pc-icon>
          <input class="filter-in" placeholder="Filter…" [value]="query()" (input)="query.set($any($event.target).value)">
        </div>
        @for (g of menuGroups(); track g.label) {
          <div class="grp-label">
            @if (g.status) { <pc-status-dot [kind]="g.status" [size]="6"></pc-status-dot> }
            <span class="grp-name">{{ g.label }}</span>
            @if (g.statusText) { <span class="grp-status">{{ g.statusText }}</span> }
          </div>
          @for (def of g.rows; track def.type) {
            <button class="row" [class.offline]="g.offline || g.unsupported" [disabled]="g.unsupported" (click)="choose(def)">
              <span>{{ def.label }}</span>
            </button>
          }
        }
      </div>
    </ng-template>
  `,
  styles: [`
    :host { display: block; }
    .trigger { padding: 11px; }
    .trigger.primary { width: 100%; }
    /* Subtle variant: a small, low-emphasis "+ Add action" used inside dense editors (stepped-switch
       bands) so the actions themselves stay the focus, not the add button. */
    .trigger.subtle { width: auto; padding: 6px 10px; background: transparent; border: 1px dashed var(--line); color: var(--text-2); font-size: 12px; box-shadow: none; }
    .trigger.subtle:hover { border-color: var(--accent, #FFB020); color: var(--text-1); }
    /* Rendered in the global cdk-overlay container, so it is never clipped by a scrolling/overflow
       ancestor (e.g. a nested .ai-body action body). Width is matched to the trigger via the overlay. */
    .menu { width: 100%; box-sizing: border-box; background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-lg); padding: 8px; box-shadow: var(--sh-pop); max-height: 360px; overflow: auto; }
    .filter { display: flex; align-items: center; gap: 8px; background: var(--input); border: 1px solid var(--line); border-radius: 8px; padding: 8px 11px; margin-bottom: 6px; color: var(--text-3); }
    .filter-in { flex: 1; min-width: 0; background: transparent; border: none; outline: none; color: var(--text-1); font-size: 12px; }
    .grp-label { display: flex; align-items: center; gap: 6px; font-family: var(--font-mono); font-size: 9px; letter-spacing: 0.12em; color: var(--text-3); padding: 10px 9px 3px; }
    .grp-name { text-transform: uppercase; }
    .grp-status { margin-left: auto; letter-spacing: 0.06em; text-transform: uppercase; }
    .row { display: flex; align-items: center; gap: 7px; width: 100%; text-align: left; border: none; background: transparent; color: var(--text-soft); font-size: 12.5px; padding: 7px 9px 7px 18px; border-radius: var(--r-sm); cursor: pointer; }
    .row:hover { background: var(--accent-tint-soft); color: var(--accent-text); }
    .row.offline { color: var(--text-3); }
    .row:disabled { cursor: not-allowed; }
    .row:disabled:hover { background: transparent; color: var(--text-3); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPickerComponent {
  private readonly integrations = inject(IntegrationDataService);
  private readonly platform = inject(PlatformService);

  /** Which control slot the picker is choosing for: 'dial' (rotate) or 'button' (press / band action). */
  readonly kind = input.required<CommandKind>();
  readonly triggerLabel = input<string>('Add action');
  /** 'primary' = the prominent full-width CTA (control page); 'subtle' = a small dashed button (band editor). */
  readonly variant = input<'primary' | 'subtle'>('primary');
  readonly pick = output<CommandDef>();

  readonly query = signal('');
  readonly open = signal(false);
  /** Overlay width, matched to the trigger so the dropdown lines up under the button. */
  readonly menuWidth = signal(300);

  /** Toggle the picker, sizing the overlay to the trigger button's current width. */
  toggle(trigger: HTMLElement): void {
    if (!this.open()) {
      this.menuWidth.set(Math.max(240, Math.round(trigger.getBoundingClientRect().width)));
    }
    this.open.set(!this.open());
  }

  /** Integration groups, in display order; each gets its own status-chip header. */
  private static readonly INTEGRATIONS: { id: Integration; label: string }[] = [
    { id: 'obs', label: 'OBS' },
    { id: 'voicemeeter', label: 'Voicemeeter' },
    { id: 'wavelink', label: 'Wave Link' },
    { id: 'discord', label: 'Discord' },
    { id: 'homeassistant', label: 'Home Assistant' },
  ];

  readonly menuGroups = computed<PickerGroup[]>(() => {
    const kind = this.kind();
    const q = this.query().trim().toLowerCase();
    const match = (d: CommandDef) => d.kinds.includes(kind) && (!q || d.label.toLowerCase().includes(q));
    const groups: PickerGroup[] = [];
    // Generic command categories first — no integration, so no status chip.
    for (const cat of ['audio', 'system'] as CommandCategory[]) {
      const rows = COMMANDS.filter(d => d.category === cat && match(d));
      if (rows.length) groups.push({ label: categoryLabel(cat), offline: false, rows });
    }
    // Then one group per integration, with the live connection status on the group header.
    for (const ig of CommandPickerComponent.INTEGRATIONS) {
      const rows = COMMANDS.filter(d => d.category === 'integration' && d.integration === ig.id && match(d));
      if (rows.length) groups.push({ label: ig.label, rows, ...this.integrationStatus(ig.id) });
    }
    return groups;
  });

  choose(def: CommandDef): void {
    this.pick.emit(def);
    this.open.set(false);
    this.query.set('');
  }

  /** Live connection status for an integration, shown once on its group header. */
  private integrationStatus(integration: Integration): { status?: Status; statusText?: string; offline: boolean; unsupported?: boolean } {
    if (integration === 'voicemeeter') {
      // Voicemeeter has no live connection signal; show it as available without a connected/offline claim.
      return this.platform.voicemeeterSupported() ? { offline: false } : { offline: false, unsupported: true, statusText: 'unavailable' };
    }
    if (integration === 'wavelink' && !this.platform.waveLinkSupported()) {
      return { offline: false, unsupported: true, statusText: 'unavailable' };
    }
    const connected = integration === 'obs' ? this.integrations.obsConnected()
      : integration === 'homeassistant' ? this.integrations.haConnected()
        : integration === 'discord' ? this.integrations.discordConnected()
          : this.integrations.waveLinkConnected();
    const loading = integration === 'obs' ? this.integrations.obsScenes.isLoading()
      : integration === 'homeassistant' ? this.integrations.haStatus.isLoading()
        : integration === 'discord' ? this.integrations.discordStatus.isLoading()
          : this.integrations.waveLink.isLoading();
    if (loading) return { status: 'connecting', statusText: 'connecting…', offline: false };
    return connected ? { status: 'ok', statusText: 'connected', offline: false } : { status: 'idle', statusText: 'not connected', offline: true };
  }
}
