import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './ui';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent],
  template: `
    <router-outlet />
    <pc-toast-host />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
}
