import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Placeholder — Action assignment screen (C). Implemented in a later step. */
@Component({
  selector: 'app-control',
  standalone: true,
  template: `<div style="padding:40px;color:var(--text-2)">Action assignment for control {{ index() }} — coming up.</div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ControlComponent {
  readonly serial = input.required<string>();
  readonly index = input.required<string>();
}
