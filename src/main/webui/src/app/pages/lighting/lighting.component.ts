import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Placeholder — Lighting editor (D). Implemented in a later step. */
@Component({
  selector: 'app-lighting',
  standalone: true,
  template: `<div style="padding:40px;color:var(--text-2)">Lighting editor for {{ serial() }} — coming up.</div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LightingComponent {
  readonly serial = input.required<string>();
}
