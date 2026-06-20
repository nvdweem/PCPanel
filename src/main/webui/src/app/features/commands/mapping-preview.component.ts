import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DialParams, mappingCurve } from './mapping-curve.util';

/**
 * Compact, non-interactive input-mapping curve — a shrunk version of the editor
 * graph so each action shows at a glance what range it maps (e.g. 0-50% vs 50-100%),
 * without having to expand it. Sized to fit inside the action header row.
 */
@Component({
  selector: 'pc-mapping-preview',
  standalone: true,
  template: `
    <svg [attr.width]="width()" [attr.height]="height()" [attr.viewBox]="'0 0 ' + width() + ' ' + height()">
      <rect x="0.5" y="0.5" [attr.width]="width() - 1" [attr.height]="height() - 1" rx="4" fill="#0E0F12" stroke="#23262E"></rect>
      <path [attr.d]="curve().path" fill="none" stroke="#FFB020" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"></path>
      <circle [attr.cx]="geom().x0" [attr.cy]="curve().y0" r="1.6" fill="#FFB020"></circle>
      <circle [attr.cx]="geom().x1" [attr.cy]="curve().y1" r="1.6" fill="#FFB020"></circle>
    </svg>
  `,
  styles: [`:host { display: inline-flex; line-height: 0; }`],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MappingPreviewComponent {
  readonly dialParams = input<DialParams | null>(null);
  readonly width = input<number>(46);
  readonly height = input<number>(26);
  private readonly pad = 4;

  readonly geom = computed(() => ({
    x0: this.pad, x1: this.width() - this.pad,
    yBottom: this.height() - this.pad, yTop: this.pad,
  }));
  readonly curve = computed(() => mappingCurve(this.dialParams(), this.geom()));
}
