import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './ui';
import { OnboardingComponent } from './onboarding.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent, OnboardingComponent],
  template: `
    <router-outlet />
    <pc-toast-host />
    <app-onboarding />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
}
