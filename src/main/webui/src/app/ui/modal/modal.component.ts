import { ChangeDetectionStrategy, Component, HostListener, input, output } from '@angular/core';

/**
 * Centered modal dialog. Controlled via [open]; projects content. Use the
 * `[modal-title]`, default, and `[modal-actions]` slots, or just project a body.
 */
@Component({
  selector: 'pc-modal',
  standalone: true,
  template: `
    @if (open()) {
      <div class="scrim" (click)="onScrim($event)">
        <div class="card" [style.width.px]="width()" (click)="$event.stopPropagation()">
          @if (heading()) { <div class="title">{{ heading() }}</div> }
          <ng-content></ng-content>
        </div>
      </div>
    }
  `,
  styles: [`
    .scrim {
      position: fixed; inset: 0; z-index: 1300; display: flex; align-items: center; justify-content: center;
      background: rgba(0,0,0,0.55); backdrop-filter: blur(2px); padding: 24px;
    }
    .card {
      background: var(--popover); border: 1px solid var(--raised-line); border-radius: var(--r-lg);
      padding: 18px; box-shadow: 0 24px 60px rgba(0,0,0,0.7); max-width: 100%;
    }
    .title { font-family: var(--font-display); font-weight: 600; font-size: 15px; margin-bottom: 6px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModalComponent {
  readonly open = input<boolean>(false);
  readonly heading = input<string>('');
  readonly width = input<number>(360);
  readonly closeOnScrim = input<boolean>(true);
  readonly dismiss = output<void>();

  onScrim(_: MouseEvent): void {
    if (this.closeOnScrim()) this.dismiss.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) this.dismiss.emit();
  }
}
