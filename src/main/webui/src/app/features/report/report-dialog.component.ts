import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';

import { ClientDiagnosticsService } from '../../services/client-diagnostics.service';
import { DeviceStateService } from '../../services/device-state.service';
import { ReportService } from '../../services/report.service';
import { ModalComponent, SelectComponent, SelectOption, SpinnerComponent, ToggleComponent, ToastService } from '../../ui';

/**
 * Collects a bug report in the shape a maintainer can act on, then hands it off: the answers and the
 * chosen attachments are written to a diagnostics bundle in the reports folder, and GitHub opens with
 * the issue already filled in for the user to attach that file to.
 *
 * The three questions are what a usable report needs and what free-form descriptions usually leave
 * out — what happened, how to get there, and what was expected instead.
 */
@Component({
  selector: 'app-report-dialog',
  standalone: true,
  imports: [ModalComponent, SelectComponent, ToggleComponent, SpinnerComponent],
  template: `
    <pc-modal [open]="report.dialogOpen()" heading="Report a problem" [width]="520" (dismiss)="close()">
      <div class="body">
        <label class="field">
          <span class="field-label">What went wrong?</span>
          <textarea class="pc-input ta" rows="3" placeholder="Volume jumps to 100% when I turn the second knob"
                    [value]="summary()" (input)="summary.set($any($event.target).value)"></textarea>
        </label>

        <label class="field">
          <span class="field-label">How can we reproduce it?</span>
          <textarea class="pc-input ta" rows="3" placeholder="1. Open Spotify&#10;2. Turn knob 2 down&#10;3. …"
                    [value]="steps()" (input)="steps.set($any($event.target).value)"></textarea>
        </label>

        <label class="field">
          <span class="field-label">What did you expect instead?</span>
          <textarea class="pc-input ta" rows="2" placeholder="The volume should stay where I set it"
                    [value]="expected()" (input)="expected.set($any($event.target).value)"></textarea>
        </label>

        <label class="field">
          <span class="field-label">Which device?</span>
          <pc-select [block]="true" [options]="deviceOptions()" [(value)]="device"></pc-select>
        </label>

        <div class="micro-label section-label">INCLUDE</div>
        <div class="pc-card pad stack">
          <div class="row">
            <div class="row-text">
              <div class="row-label">Application log</div>
              <div class="row-sub">what the app recorded while the problem happened</div>
            </div>
            <pc-toggle [value]="includeLog()" (valueChange)="includeLog.set($event)"></pc-toggle>
          </div>
          <div class="row">
            <div class="row-text">
              <div class="row-label">System information</div>
              <div class="row-sub">version, operating system, connected devices</div>
            </div>
            <pc-toggle [value]="includeSystemInfo()" (valueChange)="includeSystemInfo.set($event)"></pc-toggle>
          </div>
          <div class="row">
            <div class="row-text">
              <div class="row-label">Browser console</div>
              <div class="row-sub">errors and failed requests from this page</div>
            </div>
            <pc-toggle [value]="includeClientDiagnostics()" (valueChange)="includeClientDiagnostics.set($event)"></pc-toggle>
          </div>
          <div class="row">
            <div class="row-text">
              <div class="row-label">My configuration</div>
              <div class="row-sub">your devices, profiles and integration settings, with passwords and tokens removed</div>
            </div>
            <pc-toggle [value]="includeProfile()" (valueChange)="includeProfile.set($event)"></pc-toggle>
          </div>
        </div>

        <p class="note">A zip is saved to your reports folder. Open it and check the contents before attaching it —
          especially if you use OBS, MQTT, Home Assistant or Discord.</p>

        <div class="actions">
          <button class="pc-btn ghost" (click)="close()">Cancel</button>
          <button class="pc-btn primary" [disabled]="!canSubmit()" (click)="submit()">
            @if (saving()) { <pc-spinner [size]="13" [thickness]="2"></pc-spinner> } Continue on GitHub
          </button>
        </div>
      </div>
    </pc-modal>
  `,
  styles: [`
    .body { width: 472px; display: flex; flex-direction: column; gap: 12px; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .field-label { font-size: 11.5px; color: var(--text-2); }
    .ta { resize: vertical; line-height: 1.45; }
    .section-label { margin-top: 2px; }
    .stack { display: flex; flex-direction: column; gap: 14px; }
    .row { display: flex; align-items: center; gap: 16px; }
    .row-text { flex: 1; min-width: 0; }
    .row-label { font-size: 13.5px; color: var(--text-soft); }
    .row-sub { font-size: 11px; color: var(--text-3); margin-top: 2px; }
    .note { font-size: 11.5px; color: var(--text-3); line-height: 1.5; margin: 0; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px; }
    .pc-btn pc-spinner { margin-right: 4px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportDialogComponent {
  readonly report = inject(ReportService);
  private readonly devices = inject(DeviceStateService);
  private readonly diagnostics = inject(ClientDiagnosticsService);
  private readonly toast = inject(ToastService);

  readonly summary = signal('');
  readonly steps = signal('');
  readonly expected = signal('');
  readonly device = signal<string | undefined>('');

  readonly includeLog = signal(true);
  readonly includeSystemInfo = signal(true);
  readonly includeProfile = signal(false);
  readonly includeClientDiagnostics = signal(false);

  readonly saving = signal(false);
  readonly canSubmit = computed(() => this.summary().trim().length > 0 && !this.saving());

  readonly deviceOptions = computed<SelectOption[]>(() => [
    { value: '', label: 'Not device-specific' },
    ...Object.values(this.devices.devices()).map(d => ({ value: d.serial, label: d.displayName, hint: d.serial })),
  ]);

  constructor() {
    // Each time the dialog is raised, start from a clean form seeded with whatever raised it, and
    // offer the browser capture only when there is something in it to send.
    let wasOpen = false;
    effect(() => {
      const open = this.report.dialogOpen();
      if (open && !wasOpen) {
        this.summary.set(this.report.prefilledSummary());
        this.steps.set('');
        this.expected.set('');
        this.device.set('');
        this.includeLog.set(true);
        this.includeSystemInfo.set(true);
        this.includeProfile.set(false);
        this.includeClientDiagnostics.set(this.diagnostics.hasAnything());
      }
      wasOpen = open;
    });
  }

  close(): void {
    this.report.close();
  }

  async submit(): Promise<void> {
    if (!this.canSubmit()) return;
    this.saving.set(true);
    try {
      const includeClient = this.includeClientDiagnostics();
      const result = await this.report.submit({
        summary: this.summary(),
        steps: this.steps(),
        expected: this.expected(),
        deviceSerial: this.device() || undefined,
        includeLog: this.includeLog(),
        includeSystemInfo: this.includeSystemInfo(),
        includeProfile: this.includeProfile(),
        includeClientDiagnostics: includeClient,
        consoleEntries: includeClient ? this.diagnostics.consoleEntries() : [],
        failedRequests: includeClient ? this.diagnostics.failedRequests() : [],
      });
      this.report.close();
      window.open(result.issueUrl, '_blank', 'noopener');
      this.toast.show(`Saved ${result.fileName}`, {
        sub: 'Attach it to the issue that just opened.',
        kind: 'success',
        timeout: 12000,
        action: 'Open folder',
        onAction: () => void this.report.openReportsFolder(),
      });
    } catch {
      this.toast.show('Could not write the report bundle', { kind: 'error' });
    } finally {
      this.saving.set(false);
    }
  }
}
