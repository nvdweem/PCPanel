import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model } from '@angular/core';
import { OverlayModule } from '@angular/cdk/overlay';
import { RouterLink } from '@angular/router';
import { CommandDef, COMMAND_BY_TYPE, FieldDef, LiveSource } from './command-catalog';
import { CommandPickerComponent } from './command-picker.component';
import { mappingCurve } from './mapping-curve.util';
import { IntegrationDataService } from './integration-data.service';
import {
  AppPickerComponent, ColorPickerComponent, IconComponent, IconName, KeyRecorderComponent, SegmentedComponent,
  SelectComponent, SelectOption, ToggleComponent,
} from '../../ui';

type Cmd = Record<string, any>;

/**
 * Generic editor for one command. Renders the command's `fields` from the
 * catalog and two-way binds [(command)]. Dial commands also get the input-mapping
 * editor (invert + start/end with a curve preview), bound to `command.dialParams`.
 */
@Component({
  selector: 'pc-command-fields',
  standalone: true,
  // Self-referenced (CommandFieldsComponent) so each stepped-switch band can host a nested action editor.
  imports: [OverlayModule, RouterLink, IconComponent, ToggleComponent, SelectComponent, AppPickerComponent, KeyRecorderComponent, SegmentedComponent, ColorPickerComponent, CommandFieldsComponent, CommandPickerComponent],
  template: `
    <div class="fields">
      @for (f of visibleFields(); track f.kind + ($any(f).key || '')) {
        @switch (f.kind) {
          @case ('text') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <input class="pc-input" [class.mono]="$any(f).mono" [placeholder]="$any(f).placeholder || ''"
                     [value]="val($any(f).key)" (input)="set($any(f).key, $any($event.target).value)">
            </div>
          }
          @case ('textarea') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <textarea class="pc-input mono ta" [attr.rows]="$any(f).rows || 3" [placeholder]="$any(f).placeholder || ''"
                        [value]="val($any(f).key)" (input)="set($any(f).key, $any($event.target).value)"></textarea>
            </div>
          }
          @case ('ha-help') {
            <div class="ha-help">
              @if (haBuildUrl(); as url) {
                <a class="ha-link" [href]="url" target="_blank" rel="noopener">
                  <pc-icon name="external-link" [size]="13"></pc-icon> Build this action in Home Assistant
                </a>
              }
              <a class="ha-link" routerLink="/settings" [queryParams]="{ tab: 'homeassistant' }">
                <pc-icon name="settings" [size]="13"></pc-icon> Manage servers
              </a>
              @if ($any(f).withValue) {
                <div class="ha-hint">Use <code>{{ valueToken }}</code> where the mapped dial value should go.</div>
              }
            </div>
          }
          @case ('number') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <input class="pc-input mono" type="number" [min]="$any(f).min" [max]="$any(f).max"
                     [value]="val($any(f).key)" (input)="set($any(f).key, +$any($event.target).value)">
            </div>
          }
          @case ('toggle') {
            <div class="row-between">
              <span class="rlabel">{{ $any(f).label }}</span>
              <pc-toggle [value]="!!val($any(f).key)" (valueChange)="set($any(f).key, $event)"></pc-toggle>
            </div>
          }
          @case ('select') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="$any(f).options" [value]="val($any(f).key)"
                         (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('mute') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="muteOpts" [value]="val($any(f).key)"
                         (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('select-live') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="liveOptions($any(f).source)" [value]="val($any(f).key)"
                         [searchable]="!!$any(f).searchable" placeholder="—" (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('device') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-select [block]="true" [options]="deviceOptions($any(f).filter, $any(f).defaultLabel)" [value]="val($any(f).key)"
                         placeholder="—" (valueChange)="set($any(f).key, $event)"></pc-select>
            </div>
          }
          @case ('apps') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <div class="chips">
                @for (p of asArray($any(f).key); track p) {
                  <span class="pc-chip">{{ p }}<span class="x" (click)="removeFromArray($any(f).key, p)"><pc-icon name="x" [size]="11" [strokeWidth]="2.5"></pc-icon></span></span>
                }
                <button class="pc-chip dashed" cdkOverlayOrigin #ao="cdkOverlayOrigin" (click)="appsOpen.set($any(f).key)">
                  <pc-icon name="plus" [size]="12"></pc-icon> {{ $any(f).single ? 'choose app' : 'add app' }}
                </button>
                <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="ao" [cdkConnectedOverlayOpen]="appsOpen() === $any(f).key"
                             [cdkConnectedOverlayHasBackdrop]="true" cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                             [cdkConnectedOverlayWidth]="300" [cdkConnectedOverlayOffsetY]="6"
                             (backdropClick)="appsOpen.set(null)" (detach)="appsOpen.set(null)">
                  <pc-app-picker [items]="data.processItems()" [multi]="!$any(f).single"
                                 [value]="asArray($any(f).key)" (valueChange)="set($any(f).key, $event)"
                                 (picked)="appsOpen.set(null)"></pc-app-picker>
                </ng-template>
              </div>
            </div>
          }
          @case ('devices-list') {
            <div class="field-block">
              <div class="flabel">{{ $any(f).label }}</div>
              <pc-app-picker [items]="data.deviceItems()" [multi]="true"
                             [value]="asArray($any(f).key)" (valueChange)="set($any(f).key, $event)"
                             placeholder="Search devices…"></pc-app-picker>
            </div>
          }
          @case ('wavelink-target') {
            <div class="field-block">
              <div class="flabel">{{ wlPrimaryLabel() }}</div>
              <pc-select [block]="true" [options]="liveOptions(wlPrimarySource())" [value]="val('id1')"
                         placeholder="—" (valueChange)="set('id1', $event)"></pc-select>
            </div>
            @if (val('commandType') === 'Mix') {
              <div class="field-block">
                <div class="flabel">Mix</div>
                <pc-select [block]="true" [options]="liveOptions('wl-mixes')" [value]="val('id2')"
                           placeholder="—" (valueChange)="set('id2', $event)"></pc-select>
              </div>
            }
          }
          @case ('keystroke') {
            <div class="field-block">
              <div class="flabel">Mode</div>
              <pc-segmented [options]="keyModes" [value]="val('type')" (valueChange)="set('type', $event)"></pc-segmented>
              <div style="height:10px"></div>
              @if (val('type') === 'TEXT') {
                <input class="pc-input mono" placeholder="Text to type…" [value]="val('text')" (input)="set('text', $any($event.target).value)">
              } @else {
                <pc-key-recorder [value]="val('keystroke')" (valueChange)="set('keystroke', $event)"></pc-key-recorder>
              }
            </div>
          }
          @case ('analog-bands') {
            <div class="bands">
              <div class="bands-hint">Split the travel into positions. Entering a position runs its actions once; moving within it does nothing. Leave gaps between positions for a dead zone.</div>
              @for (b of bands(); track $index; let bi = $index) {
                <div class="band">
                  <div class="band-head">
                    <span class="band-no" [style.background]="b.color || '#3A3F4A'">{{ bi + 1 }}</span>
                    <span class="band-range">{{ b.start }}–{{ b.end }}%</span>
                    <span class="band-action-label">{{ bandSummary(b) }}</span>
                    <button class="band-del" (click)="removeBand(bi)"><pc-icon name="trash" [size]="13"></pc-icon></button>
                  </div>
                  <div class="band-grid">
                    <div class="pc-field">
                      <div class="pc-field-label">From %</div>
                      <input class="pc-field-input" type="number" min="0" max="100" [value]="b.start" (input)="setBand(bi, 'start', +$any($event.target).value)">
                    </div>
                    <div class="pc-field">
                      <div class="pc-field-label">To %</div>
                      <input class="pc-field-input" type="number" min="0" max="100" [value]="b.end" (input)="setBand(bi, 'end', +$any($event.target).value)">
                    </div>
                  </div>
                  <pc-color-picker label="Feedback colour" [value]="b.color || '#FFB020'" (valueChange)="setBand(bi, 'color', $event)"></pc-color-picker>
                  <div class="field-block">
                    <div class="flabel">Actions when entering this position</div>
                    @if (bandCmds(b).length) {
                      <div class="band-actions">
                        @for (cmd of bandCmds(b); track $index; let ci = $index) {
                          <div class="band-action-item" [class.expanded]="bandExpanded() === bandKey(bi, ci)">
                            <div class="bai-head" (click)="toggleBandAction(bi, ci)">
                              <pc-icon class="bai-icon" [name]="bandCmdIcon(cmd)" [size]="13"></pc-icon>
                              <span class="bai-name">{{ bandCmdLabel(cmd) }}</span>
                              <pc-icon class="bai-chev" [name]="bandExpanded() === bandKey(bi, ci) ? 'chevron-up' : 'chevron-down'" [size]="13"></pc-icon>
                              <button class="bai-del" (click)="$event.stopPropagation(); removeBandCmd(bi, ci)"><pc-icon name="trash" [size]="12"></pc-icon></button>
                            </div>
                            @if (bandExpanded() === bandKey(bi, ci) && bandCmdDef(cmd); as cdef) {
                              <div class="bai-body">
                                <pc-command-fields [def]="cdef" [command]="cmd" (commandChange)="setBandCmd(bi, ci, $event)" [profiles]="profiles()"></pc-command-fields>
                              </div>
                            }
                          </div>
                        }
                      </div>
                    }
                    <pc-command-picker kind="button" triggerLabel="Add action" variant="subtle" (pick)="addBandCmd(bi, $event)"></pc-command-picker>
                  </div>
                </div>
              }
              <button class="pc-btn add-band" (click)="addBand()"><pc-icon name="plus" [size]="14"></pc-icon> Add position</button>
            </div>
          }
        }
      }

      @if (showMapping() && command()['dialParams']) {
        <div class="mapping">
          <div class="flabel">Input mapping</div>
          <div class="map-row">
            <svg width="150" height="100" viewBox="0 0 150 100">
              <rect x="1" y="1" width="148" height="98" rx="8" fill="#0E0F12" stroke="#23262E"></rect>
              <line x1="18" y1="82" x2="132" y2="82" stroke="#2A2E37" stroke-width="1"></line>
              <line x1="18" y1="18" x2="18" y2="82" stroke="#2A2E37" stroke-width="1"></line>
              <path [attr.d]="curve().path" fill="none" stroke="#FFB020" stroke-width="2.5" stroke-linecap="round"></path>
              <circle [attr.cx]="18" [attr.cy]="curve().y0" r="3.5" fill="#FFB020"></circle>
              <circle [attr.cx]="132" [attr.cy]="curve().y1" r="3.5" fill="#FFB020"></circle>
              <text x="14" y="95" font-family="monospace" font-size="8" fill="#5F6671">start</text>
              <text x="112" y="95" font-family="monospace" font-size="8" fill="#5F6671">end</text>
            </svg>
            <div class="map-controls">
              <div class="row-between">
                <span class="rlabel">Invert direction</span>
                <pc-toggle [value]="!!command()['dialParams'].invert" (valueChange)="setDial('invert', $event)"></pc-toggle>
              </div>
              <div class="map-fields">
                <div class="pc-field">
                  <div class="pc-field-label">Start</div>
                  <input class="pc-field-input" type="number" min="0" max="100"
                         [value]="startDisplay()" (input)="setStart(+$any($event.target).value)">
                </div>
                <div class="pc-field">
                  <div class="pc-field-label">End</div>
                  <input class="pc-field-input" type="number" min="0" max="100"
                         [value]="endDisplay()" (input)="setEnd(+$any($event.target).value)">
                </div>
              </div>
            </div>
          </div>
        </div>
      }

      @if (!def().fields.length && !(showMapping() && command()['dialParams'])) {
        <div class="no-fields">No options for this action.</div>
      }
    </div>
  `,
  styles: [`
    .fields { display: flex; flex-direction: column; gap: 16px; }
    .field-block { display: flex; flex-direction: column; }
    .flabel { font-size: 11.5px; color: var(--text-2); margin-bottom: 8px; }
    .row-between { display: flex; align-items: center; justify-content: space-between; }
    .rlabel { font-size: 12.5px; color: var(--text-soft); }
    .chips { display: flex; gap: 8px; flex-wrap: wrap; }
    textarea.ta { resize: vertical; min-height: 60px; font-family: var(--font-mono, monospace); white-space: pre; }
    .ha-help { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 16px; margin-top: -6px; }
    .ha-link { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--accent, #FFB020); text-decoration: none; }
    .ha-link:hover { text-decoration: underline; }
    .ha-hint { flex-basis: 100%; font-size: 11.5px; color: var(--text-3); }
    .ha-hint code { font-family: var(--font-mono, monospace); color: var(--text-2); }
    .mapping { border-top: 1px solid var(--line-hair); padding-top: 16px; }
    .map-row { display: flex; gap: 18px; align-items: flex-start; }
    .map-controls { flex: 1; display: flex; flex-direction: column; gap: 11px; padding-top: 8px; }
    .map-fields { display: flex; gap: 8px; }
    .map-fields .pc-field { flex: 1; }
    .no-fields { font-size: 12.5px; color: var(--text-3); }
    .bands { display: flex; flex-direction: column; gap: 12px; }
    .bands-hint { font-size: 11.5px; color: var(--text-3); line-height: 1.45; }
    .band { display: flex; flex-direction: column; gap: 12px; padding: 12px; border: 1px solid var(--line-hair); border-radius: 10px; background: var(--surface-1, #15171C); }
    .band-head { display: flex; align-items: center; gap: 10px; }
    .band-no { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 6px; font-size: 11.5px; font-weight: 600; color: #000; flex: none; }
    .band-range { font-family: var(--font-mono, monospace); font-size: 11.5px; color: var(--text-2); }
    .band-action-label { flex: 1; font-size: 12px; color: var(--text-soft); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .band-del { background: none; border: none; color: var(--text-3); cursor: pointer; padding: 2px; display: inline-flex; }
    .band-del:hover { color: var(--danger, #FF453A); }
    .band-grid { display: flex; gap: 8px; }
    .band-grid .pc-field { flex: 1; }
    .band-actions { display: flex; flex-direction: column; gap: 8px; margin-bottom: 10px; }
    .band-action-item { border: 1px solid var(--line); border-radius: 8px; background: var(--raised, #1B1E25); overflow: hidden; }
    .band-action-item.expanded { border-color: var(--accent, #FFB020); }
    .bai-head { display: flex; align-items: center; gap: 9px; padding: 10px 12px; cursor: pointer; }
    .bai-icon { color: var(--accent, #FFB020); flex: none; }
    .bai-name { flex: 1; font-size: 13px; font-weight: 500; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bai-chev { color: var(--text-3); flex: none; }
    .bai-del { background: none; border: none; color: var(--text-3); cursor: pointer; padding: 2px; display: inline-flex; flex: none; }
    .bai-del:hover { color: var(--danger, #FF453A); }
    .bai-body { padding: 12px; border-top: 1px solid var(--line-hair); background: var(--surface-1, #15171C); }
    .add-band { display: inline-flex; align-items: center; gap: 6px; align-self: flex-start; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandFieldsComponent {
  readonly data = inject(IntegrationDataService);

  readonly def = input.required<CommandDef>();
  readonly command = model.required<Cmd>();
  readonly showMapping = input<boolean>(false);
  readonly profiles = input<string[]>([]);

  readonly appsOpen = model<string | null>(null);

  readonly muteOpts: SelectOption[] = [
    { value: 'toggle', label: 'Toggle' }, { value: 'mute', label: 'Mute' }, { value: 'unmute', label: 'Unmute' },
  ];
  readonly keyModes = [{ value: 'KEY', label: 'Key combo' }, { value: 'TEXT', label: 'Type text' }];
  /** Rendered literally in the help text (interpolating a string avoids Angular parsing the braces). */
  readonly valueToken = '{{ value }}';

  // Auto-select the only configured Home Assistant server when an action has none chosen yet, so
  // single-server setups never have to pick. With several servers the user must choose explicitly.
  private readonly _haAutoServer = effect(() => {
    if (this.def().integration !== 'homeassistant') return;
    const servers = this.data.haServers.value() ?? [];
    if (!this.command()['server'] && servers.length === 1) {
      this.set('server', servers[0].id);
    }
  });

  /** The selected server's Developer Tools → Actions page (single server auto-resolves), or null. */
  readonly haBuildUrl = computed<string | null>(() => {
    const servers = this.data.haServers.value() ?? [];
    const sel = this.command()['server'];
    const srv = servers.find(s => s.id === sel) ?? (servers.length === 1 ? servers[0] : undefined);
    if (!srv?.url) return null;
    return srv.url.replace(/\/+$/, '') + '/config/developer-tools/action';
  });

  /** Fields whose optional `showWhen` condition (another field equals a value) is met — hides irrelevant inputs. */
  visibleFields(): FieldDef[] {
    return this.def().fields.filter(f => {
      const w = (f as any).showWhen as { key: string; equals: string } | undefined;
      return !w || this.val(w.key) === w.equals;
    });
  }

  val(key: string): any { return this.command()[key]; }
  asArray(key: string): string[] { const v = this.command()[key]; return Array.isArray(v) ? v : []; }

  set(key: string, value: any): void {
    this.command.update(c => ({ ...c, [key]: value }));
  }
  setDial(key: string, value: any): void {
    this.command.update(c => ({ ...c, dialParams: { ...(c['dialParams'] ?? {}), [key]: value } }));
  }
  removeFromArray(key: string, item: string): void {
    this.set(key, this.asArray(key).filter(x => x !== item));
  }

  liveOptions(source: LiveSource): SelectOption[] {
    switch (source) {
      case 'obs-scenes': return strOpts(this.data.obsScenes.value());
      case 'obs-sources': return strOpts(this.data.obsSources.value());
      case 'vm-advanced': return (this.data.vmAdvanced.value() ?? []).flatMap(g => g.params.map(p => ({ value: p, label: p })));
      case 'wl-channels': return wlOpts(this.data.wlChannels());
      case 'wl-inputs': return wlOpts(this.data.wlInputs());
      case 'wl-mixes': return wlOpts(this.data.wlMixes());
      case 'wl-outputs': return wlOpts(this.data.wlOutputs());
      case 'discord-users': return (this.data.discordUsers.value() ?? []).map(u => ({
        value: u.username, label: u.inVoice ? `${u.displayName} (in call)` : u.displayName,
      }));
      case 'discord-channels': return (this.data.discordChannels.value() ?? []).map(c => ({
        value: c.id, label: c.guildName ? `${c.name} — ${c.guildName}` : c.name,
      }));
      case 'discord-mute-targets': return [{ value: 'self', label: 'Self (your mic)' }, ...this.discordUserOpts()];
      case 'discord-volume-targets': return [
        { value: 'mic', label: 'Your mic' }, { value: 'output', label: 'Your output' }, ...this.discordUserOpts(),
      ];
      case 'profiles': return this.profiles().map(p => ({ value: p, label: p }));
      case 'ha-servers': return (this.data.haServers.value() ?? []).map(s => ({ value: s.id, label: s.name }));
      default: return [];
    }
  }

  /** User targets ordered friends-first (by name), then everyone else (by name) — appended after the fixed options. */
  private discordUserOpts(): SelectOption[] {
    const byName = (a: { displayName: string }, b: { displayName: string }) => a.displayName.localeCompare(b.displayName);
    const users = this.data.discordUsers.value() ?? [];
    const friends = users.filter(u => u.friend).sort(byName);
    const others = users.filter(u => !u.friend).sort(byName);
    return [...friends, ...others].map(u => ({
      value: u.username, label: u.inVoice ? `${u.displayName} (in call)` : u.displayName,
    }));
  }

  // ── Wave Link target: id1's list (and its label) follow the chosen commandType.
  //    Mix additionally needs a mix (id2), handled in the template. Channel/Input/Output use id1 only.
  wlPrimarySource(): LiveSource {
    switch (this.val('commandType')) {
      case 'Input': return 'wl-inputs';
      case 'Output': return 'wl-outputs';
      default: return 'wl-channels';   // Channel and Mix both pick a channel first
    }
  }
  wlPrimaryLabel(): string {
    switch (this.val('commandType')) {
      case 'Input': return 'Input';
      case 'Output': return 'Output';
      default: return 'Channel';
    }
  }

  deviceOptions(filter: 'output' | 'input' | 'all' | undefined, defaultLabel?: string): SelectOption[] {
    const res = filter === 'output' ? this.data.outputDevices : filter === 'input' ? this.data.inputDevices : this.data.audioDevices;
    const opts = (res.value() ?? []).map(d => ({ value: d.id, label: d.name }));
    // For commands where a blank device means "use the default device", make that explicitly selectable.
    return defaultLabel ? [{ value: '', label: defaultLabel }, ...opts] : opts;
  }

  // ── input mapping (Start/End as displayed positions 0..100) ──────────────────
  // Backend stores moveStart (position from bottom) and moveEnd (amount trimmed
  // from the top). The user-facing "End" is the end *position* = 100 - moveEnd,
  // so the default (moveStart 0, moveEnd 0) reads as Start 0 / End 100 = full range.
  readonly startDisplay = computed(() => clamp(this.command()['dialParams']?.moveStart ?? 0));
  readonly endDisplay = computed(() => clamp(100 - (this.command()['dialParams']?.moveEnd ?? 0)));

  setStart(v: number): void { this.setDial('moveStart', clamp(v)); }
  setEnd(v: number): void { this.setDial('moveEnd', clamp(100 - clamp(v))); }

  // Transfer curve on the 150x100 graph (x 18..132, y 82(0%)..24(100%)), via the shared mapping math.
  readonly curve = computed(() => mappingCurve(this.command()['dialParams'], { x0: 18, x1: 132, yBottom: 82, yTop: 24 }));

  // ── stepped-switch bands ─────────────────────────────────────────────────────
  // Each band holds a Commands list ({ commands: [...], type }); a position can run any number of
  // button-kind actions on entry, chosen through the shared CommandPickerComponent (same categorized +
  // filterable menu as the control page) and edited inline, mirroring the control page's action list.
  private readonly bandPalette = ['#FF3B30', '#34C759', '#0A84FF', '#FFD60A', '#AF52DE', '#FF9F0A', '#5AC8FA', '#FF2D55'];

  /** Which band action's editor is expanded, keyed "{bandIndex}:{cmdIndex}"; null = all collapsed. */
  readonly bandExpanded = model<string | null>(null);

  bands(): any[] { const b = this.command()['bands']; return Array.isArray(b) ? b : []; }
  bandCmds(b: any): Cmd[] { const c = b?.commands?.commands; return Array.isArray(c) ? c : []; }
  bandCmdDef(cmd: Cmd): CommandDef | undefined { const t = cmd?.['_type']; return t ? COMMAND_BY_TYPE.get(t) : undefined; }
  bandCmdLabel(cmd: Cmd): string { return this.bandCmdDef(cmd)?.label ?? String(cmd?.['_type'] ?? '').split('.').pop() ?? 'Action'; }
  bandCmdIcon(cmd: Cmd): IconName { return this.bandCmdDef(cmd)?.icon ?? 'zap'; }
  bandKey(bandIdx: number, cmdIdx: number): string { return bandIdx + ':' + cmdIdx; }

  bandSummary(b: any): string {
    const n = this.bandCmds(b).length;
    return n === 0 ? 'No actions' : n === 1 ? this.bandCmdLabel(this.bandCmds(b)[0]) : n + ' actions';
  }

  toggleBandAction(bandIdx: number, cmdIdx: number): void {
    const key = this.bandKey(bandIdx, cmdIdx);
    this.bandExpanded.set(this.bandExpanded() === key ? null : key);
  }

  private updateBand(i: number, patch: Record<string, any>): void {
    const bands = this.bands().map((b, k) => k === i ? { ...b, ...patch } : b);
    this.set('bands', bands);
  }

  private setBandCmds(i: number, cmds: Cmd[]): void {
    const type = this.bands()[i]?.commands?.type ?? 'allAtOnce';
    this.updateBand(i, { commands: { commands: cmds, type } });
  }

  setBand(i: number, key: 'start' | 'end' | 'color', value: any): void {
    this.updateBand(i, { [key]: key === 'color' ? value : clamp(value) });
  }

  removeBand(i: number): void {
    this.set('bands', this.bands().filter((_, k) => k !== i));
  }

  addBand(): void {
    const bands = this.bands();
    const last = bands[bands.length - 1];
    // Contiguous, consistent-width positions: 0–33, 33–66, 66–100, … (snap the final one to 100).
    const start = last ? clamp(last.end) : 0;
    const end = start + 33 >= 100 - 33 ? 100 : start + 33;
    const color = this.bandPalette[bands.length % this.bandPalette.length];
    this.set('bands', [...bands, { start, end, color, commands: { commands: [], type: 'allAtOnce' } }]);
  }

  /** Append a fresh instance of the chosen command type to band i's actions, and expand it for editing. */
  addBandCmd(i: number, def: CommandDef): void {
    const cmds = [...this.bandCmds(this.bands()[i]), def.buildEmpty() as Cmd];
    this.setBandCmds(i, cmds);
    this.bandExpanded.set(this.bandKey(i, cmds.length - 1));
  }

  removeBandCmd(i: number, j: number): void {
    this.setBandCmds(i, this.bandCmds(this.bands()[i]).filter((_, k) => k !== j));
  }

  setBandCmd(i: number, j: number, cmd: Cmd): void {
    this.setBandCmds(i, this.bandCmds(this.bands()[i]).map((c, k) => k === j ? cmd : c));
  }
}

function clamp(v: number): number { return Math.max(0, Math.min(100, v ?? 0)); }
function strOpts(arr: string[] | undefined): SelectOption[] { return (arr ?? []).map(s => ({ value: s, label: s })); }
function wlOpts(arr: { id: string; name?: string }[]): SelectOption[] { return arr.map(i => ({ value: i.id, label: i.name ?? i.id })); }
