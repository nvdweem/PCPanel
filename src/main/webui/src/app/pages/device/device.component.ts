import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Placeholder — Device configuration / Advanced (B). Implemented in a later step. */
@Component({
  selector: 'app-device',
  standalone: true,
  template: `<div style="padding:40px;color:var(--text-2)">Device configuration for {{ serial() }} — coming up.</div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeviceComponent {
  readonly serial = input.required<string>();
}
