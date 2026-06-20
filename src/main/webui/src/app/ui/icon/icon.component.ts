import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Lucide-style line icons rendered as inline SVG. `name` selects from the
 * curated registry below; `size` and `strokeWidth` tune the glyph. Colour comes
 * from `currentColor`, so set `color` on the host/parent to recolour.
 */
export type IconName =
  | 'chevron-down' | 'chevron-up' | 'chevron-left' | 'chevron-right'
  | 'plus' | 'x' | 'check' | 'search' | 'settings' | 'pencil' | 'trash'
  | 'star' | 'sun' | 'ring' | 'grip' | 'more-vertical' | 'more-horizontal'
  | 'mic' | 'mic-off' | 'volume' | 'volume-x' | 'monitor' | 'log-out'
  | 'window' | 'sliders' | 'grid' | 'refresh' | 'download' | 'alert-triangle'
  | 'plug' | 'usb' | 'keyboard' | 'play' | 'film' | 'wave' | 'lightbulb'
  | 'cable' | 'zap' | 'copy' | 'clipboard' | 'eraser' | 'arrow-down' | 'gamepad';

const PATHS: Record<string, string> = {
  'chevron-down': '<polyline points="6 9 12 15 18 9"/>',
  'chevron-up': '<polyline points="18 15 12 9 6 15"/>',
  'chevron-left': '<polyline points="15 18 9 12 15 6"/>',
  'chevron-right': '<polyline points="9 18 15 12 9 6"/>',
  'plus': '<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>',
  'x': '<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>',
  'check': '<polyline points="20 6 9 17 4 12"/>',
  'search': '<circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.5" y2="16.5"/>',
  'settings': '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06a1.65 1.65 0 00.33-1.82 1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06a1.65 1.65 0 001.82.33H9a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/>',
  'pencil': '<path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.1 2.1 0 013 3L12 15l-4 1 1-4z"/>',
  'trash': '<polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/>',
  'star': '<polygon points="12 2 15 9 22 9 16.5 13.5 18.5 21 12 16.5 5.5 21 7.5 13.5 2 9 9 9"/>',
  'sun': '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  'ring': '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3.5"/>',
  'grip': '<circle cx="9" cy="6" r="1.4"/><circle cx="15" cy="6" r="1.4"/><circle cx="9" cy="12" r="1.4"/><circle cx="15" cy="12" r="1.4"/><circle cx="9" cy="18" r="1.4"/><circle cx="15" cy="18" r="1.4"/>',
  'more-vertical': '<circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/>',
  'more-horizontal': '<circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/>',
  'mic': '<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10a7 7 0 0014 0M12 18v3"/>',
  'mic-off': '<line x1="2" y1="2" x2="22" y2="22"/><path d="M9 9v3a3 3 0 005 2.2M15 9.3V4a3 3 0 00-5.7-1.3"/><path d="M17 16.95A7 7 0 015 12"/>',
  'volume': '<path d="M3 10v4a2 2 0 002 2h2l4 4V4L7 8H5a2 2 0 00-2 2z"/><path d="M16 8a5 5 0 010 8"/>',
  'volume-x': '<path d="M3 10v4a2 2 0 002 2h2l4 4V4L7 8H5a2 2 0 00-2 2z"/><line x1="22" y1="9" x2="16" y2="15"/><line x1="16" y1="9" x2="22" y2="15"/>',
  'monitor': '<rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/>',
  'log-out': '<path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>',
  'window': '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18"/>',
  'sliders': '<line x1="4" y1="21" x2="4" y2="14"/><line x1="4" y1="10" x2="4" y2="3"/><line x1="12" y1="21" x2="12" y2="12"/><line x1="12" y1="8" x2="12" y2="3"/><line x1="20" y1="21" x2="20" y2="16"/><line x1="20" y1="12" x2="20" y2="3"/><line x1="1" y1="14" x2="7" y2="14"/><line x1="9" y1="8" x2="15" y2="8"/><line x1="17" y1="16" x2="23" y2="16"/>',
  'grid': '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  'refresh': '<polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>',
  'download': '<path d="M12 3v12"/><polyline points="7 10 12 15 17 10"/><path d="M5 21h14"/>',
  'alert-triangle': '<path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
  'plug': '<path d="M12 22v-5M9 8V2M15 8V2M5 8h14v3a7 7 0 01-14 0z"/>',
  'usb': '<circle cx="10" cy="7" r="1"/><circle cx="4" cy="20" r="1"/><path d="M4.7 19.3L19 5M21 3l-3 1 2 2 1-3zM9.26 7.68L5 12l2 5M10 14l5 2 3.5-3.5M18 12l-1.5-1.5"/>',
  'keyboard': '<rect x="2" y="6" width="20" height="12" rx="2"/><path d="M6 10h0M10 10h0M14 10h0M18 10h0M6 14h0M18 14h0M10 14h4"/>',
  'play': '<polygon points="5 3 19 12 5 21 5 3"/>',
  'film': '<rect x="2" y="2" width="20" height="20" rx="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/>',
  'wave': '<path d="M2 12c2-4 4-4 6 0s4 4 6 0 4-4 6 0"/>',
  'lightbulb': '<path d="M9 18h6M10 22h4M12 2a7 7 0 00-4 12.7c.6.5 1 1.3 1 2.1V18h6v-1.2c0-.8.4-1.6 1-2.1A7 7 0 0012 2z"/>',
  'cable': '<path d="M4 9V5a2 2 0 012-2h0a2 2 0 012 2v12a2 2 0 002 2h4a2 2 0 002-2v-2M16 15V5a2 2 0 00-2-2"/>',
  'zap': '<polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>',
  'copy': '<rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>',
  'clipboard': '<path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/><rect x="8" y="2" width="8" height="4" rx="1"/>',
  'eraser': '<path d="M20 20H7L3 16a2 2 0 010-3l9-9a2 2 0 013 0l5 5a2 2 0 010 3l-7 7"/><line x1="18" y1="13" x2="9" y2="22"/>',
  'arrow-down': '<line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/>',
  'gamepad': '<line x1="6" y1="12" x2="10" y2="12"/><line x1="8" y1="10" x2="8" y2="14"/><line x1="15" y1="13" x2="15.01" y2="13"/><line x1="18" y1="11" x2="18.01" y2="11"/><rect x="2" y="6" width="20" height="12" rx="2"/>',
};

@Component({
  selector: 'pc-icon',
  standalone: true,
  template: `<span class="pc-icon" [innerHTML]="svg()"></span>`,
  styles: [`
    /* inline-flex host (not the default inline) so the glyph isn't pushed up by the parent's
       inherited line-height leading — keeps icons optically centred inside icon buttons. */
    :host { display: inline-flex; line-height: 0; }
    .pc-icon { display: inline-flex; line-height: 0; }
    .pc-icon ::ng-deep svg { display: block; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly name = input.required<IconName>();
  readonly size = input<number>(16);
  readonly strokeWidth = input<number>(2);

  readonly svg = computed<SafeHtml>(() => {
    const body = PATHS[this.name()] ?? '';
    const s = this.size();
    const markup =
      `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" ` +
      `stroke="currentColor" stroke-width="${this.strokeWidth()}" ` +
      `stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;
    return this.sanitizer.bypassSecurityTrustHtml(markup);
  });
}
