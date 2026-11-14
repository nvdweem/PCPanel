import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { SelectComponent, SelectOption } from '../../ui';
import { IntegrationDataService } from '../commands/integration-data.service';

/** The value format the backend reads: '' follows the control, a device name, `app:<exe>`, or a Voicemeeter
 *  strip/bus as `VoiceMeeter: Input|Output <n>, Mute` (n 1-based). */
export const APP_TARGET_PREFIX = 'app:';

const VM_MUTE_PARAM = /^(Strip|Bus)\[(\d+)]\.Mute$/i;

/** The mute target of a Voicemeeter strip or bus, from its `Strip[0].Mute`-style parameter. */
export function voicemeeterMuteTarget(param: string): string | null {
  const m = VM_MUTE_PARAM.exec(param);
  return m ? `VoiceMeeter: ${m[1].toLowerCase() === 'bus' ? 'Output' : 'Input'} ${+m[2] + 1}, Mute` : null;
}

/**
 * Picks what a light reacts to: the control's own target, an audio device (by name) or an app. Shared by the
 * muted colour ("follows") and the audio-level light ("level of"), so both offer the same choices. With
 * `[voicemeeter]` it also lists Voicemeeter's strips and buses, whose own Mute button is then followed.
 */
@Component({
  selector: 'pc-light-target',
  standalone: true,
  imports: [SelectComponent],
  template: `
    <div class="lt">
      <span class="lt-lbl">{{ label() }}</span>
      <pc-select [options]="options()" [value]="value() ?? ''" [block]="true" [searchable]="true" [allowCustom]="true"
                 (valueChange)="valueChange.emit($any($event) ?? '')"></pc-select>
    </div>
  `,
  styles: [`
    .lt { display: flex; flex-direction: column; gap: 5px; }
    .lt-lbl { font-size: 11.5px; color: var(--text-2); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LightTargetComponent {
  private readonly data = inject(IntegrationDataService);

  readonly label = input<string>('Follows');
  /** Caption of the "follow this control" choice. */
  readonly followLabel = input<string>('This control');
  readonly value = input<string | null | undefined>('');
  /** Also offer Voicemeeter's strips and buses (only the muted colour can follow those). */
  readonly voicemeeter = input(false);
  readonly valueChange = output<string>();

  readonly options = computed<SelectOption<string>[]>(() => [
    { value: '', label: this.followLabel() },
    ...this.data.deviceItems().map(d => ({ value: d.label, label: d.label + (d.sub ? ` (${d.sub.toLowerCase()})` : '') })),
    ...this.data.processItems().map(p => ({ value: APP_TARGET_PREFIX + p.key, label: p.label + ' (app)' })),
    ...(this.voicemeeter() ? this.voicemeeterItems() : []),
  ]);

  private voicemeeterItems(): SelectOption<string>[] {
    return (this.data.vmAdvancedButtons.value() ?? []).flatMap(group => {
      const target = group.params.map(voicemeeterMuteTarget).find(t => !!t);
      return target ? [{ value: target, label: `Voicemeeter ${group.name}` }] : [];
    });
  }
}
