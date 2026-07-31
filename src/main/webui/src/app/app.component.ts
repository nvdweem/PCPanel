import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './ui';
import { OnboardingComponent } from './onboarding.component';
import { AuthGateComponent } from './auth-gate.component';
import { ReportDialogComponent } from './features/report/report-dialog.component';
import { ReportService } from './services/report.service';

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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly report = inject(ReportService);

  constructor() {
    // The tray's "Report a problem" lands on the start page carrying ?report=1. The dialog is mounted
    // here at the root, so raise it from here and strip the flag again — otherwise a reload, or
    // navigating back to this URL, would keep reopening it.
    this.route.queryParams.pipe(takeUntilDestroyed()).subscribe(params => {
      if (params['report'] === undefined) return;
      this.report.open();
      void this.router.navigate([], { queryParams: { report: null }, queryParamsHandling: 'merge', replaceUrl: true });
    });
  }
}
