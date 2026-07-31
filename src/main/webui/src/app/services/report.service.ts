import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { BugReportRequest, BugReportResponse } from '../models/generated/backend.types';

/**
 * Opens the report dialog and files what it collected. The dialog is mounted at the app root, so any
 * page — and any error toast — can raise it through {@link open}.
 */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  readonly dialogOpen = signal(false);
  /** Seeds the description when the dialog is raised from something that already failed. */
  readonly prefilledSummary = signal('');

  open(summary = ''): void {
    this.prefilledSummary.set(summary);
    this.dialogOpen.set(true);
  }

  close(): void {
    this.dialogOpen.set(false);
  }

  submit(request: BugReportRequest): Promise<BugReportResponse> {
    return firstValueFrom(this.http.post<BugReportResponse>('/api/report', request));
  }

  openReportsFolder(): Promise<unknown> {
    return firstValueFrom(this.http.post('/api/report/open', {}));
  }
}
