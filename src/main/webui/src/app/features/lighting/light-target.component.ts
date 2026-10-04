import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { SelectComponent, SelectOption } from '../../ui';
import { IntegrationDataService } from '../commands/integration-data.service';

/** The value format the backend reads: '' follows the control, a device name, or `app:<exe>`. */
export const APP_TARGET_PREFIX = 'app:';

/**
 * Picks what a light reacts to: the control's own target, an audio device (by name) or an app. Shared by the
 * muted colour ("follows") and the audio-level light ("level of"), so both offer the same choices. Free text is
 * allowed for targets that are not listed, such as a VoiceMeeter strip.
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
  readonly valueChange = output<string>();

  readonly options = computed<SelectOption<string>[]>(() => [
    { value: '', label: this.followLabel() },
    ...this.data.deviceItems().map(d => ({ value: d.label, label: d.label + (d.sub ? ` (${d.sub.toLowerCase()})` : '') })),
    ...this.data.processItems().map(p => ({ value: APP_TARGET_PREFIX + p.key, label: p.label + ' (app)' })),
  ]);
}
