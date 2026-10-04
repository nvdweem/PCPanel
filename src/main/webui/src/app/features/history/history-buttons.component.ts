import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { IconComponent } from '../../ui';
import { HistoryService } from '../../services/history.service';

/**
 * Undo / redo buttons, in the same place on every page: the right end of a page's 56px top bar, or — on pages
 * without one — pinned to that same spot ({@link corner}).
 */
@Component({
  selector: 'pc-history-buttons',
  standalone: true,
  imports: [IconComponent],
  template: `
    <button class="pc-icon-btn" title="Undo" aria-label="Undo" [disabled]="!history.canUndo()" (click)="history.undo()">
      <pc-icon name="undo" [size]="17" [strokeWidth]="1.8"></pc-icon>
    </button>
    <button class="pc-icon-btn" title="Redo" aria-label="Redo" [disabled]="!history.canRedo()" (click)="history.redo()">
      <pc-icon name="redo" [size]="17" [strokeWidth]="1.8"></pc-icon>
    </button>
  `,
  host: { '[class.corner]': 'corner()' },
  styles: [`
    :host { display: inline-flex; align-items: center; gap: 12px; }
    /* Where the buttons sit in a 56px top bar with 22px side padding. */
    :host(.corner) { position: fixed; top: 11px; right: 22px; z-index: 50; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryButtonsComponent {
  readonly history = inject(HistoryService);
  /** Pinned to the top-right corner, for pages without a top bar. */
  readonly corner = input(false);
}
