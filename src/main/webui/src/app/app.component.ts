import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { ToastHostComponent } from './ui';
import { OnboardingComponent } from './onboarding.component';
import { AuthGateComponent } from './auth-gate.component';
import { ReportDialogComponent } from './features/report/report-dialog.component';
import { ReportService } from './services/report.service';
import { HistoryService } from './services/history.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHostComponent, OnboardingComponent, AuthGateComponent, ReportDialogComponent],
  template: `
    <router-outlet />
    <pc-toast-host />
    <app-onboarding />
    <app-auth-gate />
    <app-report-dialog />
    <!-- A new element per undo/redo, so its fade plays each time: the page visibly changed. -->
    @for (n of [history.flashes()]; track n) {
      @if (n) { <div class="history-flash" aria-hidden="true"></div> }
    }
  `,
  styles: [`
    .history-flash {
      position: fixed; inset: 0; z-index: 9000; pointer-events: none;
      box-shadow: inset 0 0 0 3px var(--accent, #FFB020); background: color-mix(in srgb, var(--accent, #FFB020) 7%, transparent);
      animation: history-flash 700ms ease-out forwards;
    }
    @keyframes history-flash { from { opacity: 1; } to { opacity: 0; } }
    @media (prefers-reduced-motion: reduce) { .history-flash { animation-duration: 1ms; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly report = inject(ReportService);
  readonly history = inject(HistoryService);

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
