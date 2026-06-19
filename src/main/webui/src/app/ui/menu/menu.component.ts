import { ChangeDetectionStrategy, Component, input, model, output } from '@angular/core';
import { ConnectedPosition, OverlayModule } from '@angular/cdk/overlay';
import { CdkOverlayOrigin } from '@angular/cdk/overlay';
import { IconComponent, IconName } from '../icon/icon.component';

export interface MenuItem {
  label: string;
  icon?: IconName;
  value?: string;
  danger?: boolean;
  separatorBefore?: boolean;
  active?: boolean;
}

/** Popover menu (⋮ overflow menus, right-click context menus, tray menu). */
@Component({
  selector: 'pc-menu',
  standalone: true,
  imports: [OverlayModule, IconComponent],
  template: `
    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="origin()" [cdkConnectedOverlayOpen]="open()"
                 [cdkConnectedOverlayHasBackdrop]="true" cdkConnectedOverlayBackdropClass="cdk-overlay-transparent-backdrop"
                 [cdkConnectedOverlayPositions]="positions" [cdkConnectedOverlayOffsetY]="6"
                 (backdropClick)="open.set(false)" (detach)="open.set(false)">
      <div class="menu" [style.width.px]="width()">
        @for (item of items(); track $index) {
          @if (item.separatorBefore) { <div class="sep"></div> }
          <button type="button" class="item" [class.danger]="item.danger" [class.active]="item.active"
                  (click)="choose(item)">
            @if (item.icon) { <pc-icon [name]="item.icon" [size]="14"></pc-icon> }
            <span>{{ item.label }}</span>
          </button>
        }
      </div>
    </ng-template>
  `,
  styles: [`
    .menu {
      background: #16181D; border: 1px solid var(--raised-line); border-radius: var(--r-md);
      padding: 5px; box-shadow: var(--sh-menu);
    }
    .item {
      display: flex; align-items: center; gap: 9px; width: 100%; text-align: left;
      border: none; background: transparent; color: var(--text-soft); cursor: pointer;
      font-family: var(--font-ui); font-size: 12.5px; padding: 7px 10px; border-radius: var(--r-sm);
    }
    .item:hover { background: var(--line); color: var(--text-1); }
    .item.active { background: var(--line); color: var(--text-1); }
    .item.danger { color: var(--err-text); }
    .item pc-icon { color: var(--text-2); }
    .sep { height: 1px; background: var(--line); margin: 4px 6px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MenuComponent {
  readonly origin = input.required<CdkOverlayOrigin>();
  readonly items = input.required<MenuItem[]>();
  readonly open = model<boolean>(false);
  readonly width = input<number>(188);
  readonly select = output<MenuItem>();

  readonly positions: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top' },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom' },
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top' },
  ];

  choose(item: MenuItem): void {
    this.select.emit(item);
    this.open.set(false);
  }
}
