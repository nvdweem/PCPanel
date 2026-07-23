import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './ui';
import { OnboardingComponent } from './onboarding.component';
import { AuthGateComponent } from './auth-gate.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent, OnboardingComponent, AuthGateComponent],
  template: `
    <router-outlet />
    <pc-toast-host />
    <app-onboarding />
    <app-auth-gate />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
}
