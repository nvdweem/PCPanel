import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Placeholder — Settings (E). Implemented in a later step. */
@Component({
  selector: 'app-settings',
  standalone: true,
  template: `<div style="padding:40px;color:var(--text-2)">Settings — coming up.</div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
}
