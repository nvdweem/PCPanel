import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ColorPickerComponent, ToggleComponent } from '../../ui';

/** Off is its own state: only a blank/absent colour means off, so black (#000000) is a usable mute colour. */
const isOff = (c: string | undefined): boolean => !c || !c.trim();

/**
 * The per-control "change colour when muted" editor — an explicit on/off toggle plus a colour picker
 * shown only when enabled. This is the single source of truth for mute-override editing, used by both
 * the per-control rail editor and the full lighting page, so the two can never diverge.
 *
 * Enabled/disabled is a separate state from the colour: a blank colour means off, while any explicit
 * colour — including black — is honoured. Enabling defaults to red rather than a confusing black swatch.
 * Binds via `[color]` / `(colorChange)`.
 */
@Component({
  selector: 'pc-mute-override-field',
  standalone: true,
  imports: [ColorPickerComponent, ToggleComponent],
  template: `
    <div class="mute-row">
      <span class="mute-lbl">Change colour when muted</span>
      <pc-toggle [value]="on()" (valueChange)="setEnabled($event)"></pc-toggle>
    </div>
    @if (on()) {
      <pc-color-picker label="Muted colour" [value]="color() || '#FF0000'" (valueChange)="colorChange.emit($event)"></pc-color-picker>
    }
  `,
  styles: [`
    .mute-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .mute-lbl { font-size: 11.5px; color: var(--text-2); }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MuteOverrideFieldComponent {
  /** Current muted colour (blank/black = off). */
  readonly color = input<string>();
  /** Emits the new muted colour: a hex when enabled, '' when disabled. */
  readonly colorChange = output<string>();

  readonly on = computed(() => !isOff(this.color()));

  /** Enabling keeps an existing colour (incl. black) or defaults to red; disabling clears the colour. */
  setEnabled(enabled: boolean): void {
    const cur = this.color();
    this.colorChange.emit(enabled ? (isOff(cur) ? '#FF0000' : cur!) : '');
  }
}
