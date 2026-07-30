import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './ui';
import { OnboardingComponent } from './onboarding.component';
import { AuthGateComponent } from './auth-gate.component';
import { ReportDialogComponent } from './features/report/report-dialog.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent, OnboardingComponent, AuthGateComponent, ReportDialogComponent],
  template: `
    <router-outlet />
    <pc-toast-host />
    <app-onboarding />
    <app-auth-gate />
    <app-report-dialog />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
}
