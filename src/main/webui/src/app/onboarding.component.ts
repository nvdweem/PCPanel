import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { OnboardingService } from './services/onboarding.service';
import { SettingsService } from './services/settings.service';
import { PlatformService } from './services/platform.service';
import { IconComponent, ModalComponent, ToggleComponent } from './ui';

const GITHUB_URL = 'https://github.com/nvdweem/PCPanel';

/**
 * Shows the first-run welcome dialog or the post-install/update dialog once on startup, based on the
 * backend onboarding hint. Both offer the "open in the browser on startup" setting and explain that the
 * app keeps running in the tray. Hosted at the app root so it overlays regardless of route.
 */
@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [ModalComponent, ToggleComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- First run -->
    <pc-modal [open]="view() === 'new-user'" heading="Welcome to PCPanel" [width]="540" (dismiss)="dismiss()">
      <div class="ob">
        <p class="lead">Thanks for installing PCPanel — third-party control software for your PCPanel device.</p>
        <p class="body">Plug your device in over USB and it's detected automatically. Click a knob, slider or button on
          the device view to assign what it controls (app/device volume, mute, media keys, shortcuts, OBS, and more).</p>
        <a class="link" [href]="githubUrl" target="_blank" rel="noopener noreferrer">
          <pc-icon name="external-link" [size]="13"></pc-icon> Setup &amp; usage instructions on GitHub
        </a>
        <div class="tray-note">{{ trayNote() }}</div>
        <div class="startup-row">
          <div class="startup-text">
            <div class="startup-label">Open PCPanel in your browser when it starts</div>
            <div class="startup-sub">Off by default — PCPanel runs in the background and you reopen this page when you need it.</div>
          </div>
          <pc-toggle [value]="openOnStartup()" (valueChange)="setOpenOnStartup($event)"></pc-toggle>
        </div>
        <div class="actions"><button class="pc-btn primary" (click)="dismiss()">Get started</button></div>
      </div>
    </pc-modal>

    <!-- After an installer update -->
    <pc-modal [open]="view() === 'post-install'" heading="PCPanel is up to date" [width]="540" (dismiss)="dismiss()">
      <div class="ob">
        <p class="lead">PCPanel has been updated{{ version() ? ' to ' + version() : '' }} and is running again.</p>
        @if (changelogUrl()) {
          <a class="link" [href]="changelogUrl()" target="_blank" rel="noopener noreferrer">
            <pc-icon name="external-link" [size]="13"></pc-icon> What's new in this version
          </a>
        }
        <div class="tray-note">{{ trayNote() }}</div>
        <div class="startup-row">
          <div class="startup-text">
            <div class="startup-label">Open PCPanel in your browser when it starts</div>
            <div class="startup-sub">Off by default — PCPanel runs in the background and you reopen this page when you need it.</div>
          </div>
          <pc-toggle [value]="openOnStartup()" (valueChange)="setOpenOnStartup($event)"></pc-toggle>
        </div>
        <div class="actions"><button class="pc-btn primary" (click)="dismiss()">Done</button></div>
      </div>
    </pc-modal>
  `,
  styles: [`
    .ob { width: 480px; max-width: 100%; display: flex; flex-direction: column; gap: 14px; }
    .lead { font-size: 14px; color: var(--text-1); margin: 0; line-height: 1.5; }
    .body { font-size: 12.5px; color: var(--text-2); margin: 0; line-height: 1.6; }
    .link { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: var(--accent); text-decoration: none; width: fit-content; }
    .link:hover { text-decoration: underline; }
    .tray-note { font-size: 12px; color: var(--text-2); line-height: 1.55; background: var(--panel); border: 1px solid var(--line); border-radius: var(--r-md); padding: 11px 13px; }
    .startup-row { display: flex; align-items: center; gap: 16px; padding-top: 2px; }
    .startup-text { flex: 1; }
    .startup-label { font-size: 13px; color: var(--text-1); font-weight: 500; }
    .startup-sub { font-size: 11.5px; color: var(--text-3); line-height: 1.5; margin-top: 2px; }
    .actions { display: flex; justify-content: flex-end; margin-top: 4px; }
  `],
})
export class OnboardingComponent {
  private readonly onboarding = inject(OnboardingService);
  private readonly settings = inject(SettingsService);
  private readonly platform = inject(PlatformService);

  readonly githubUrl = GITHUB_URL;
  private readonly dismissed = signal(false);
  private readonly info = this.onboarding.info;

  readonly version = computed(() => this.info()?.version ?? '');
  readonly changelogUrl = computed(() => this.info()?.changelogUrl ?? '');
  readonly openOnStartup = computed(() => this.settings.settings.value()?.openBrowserOnStartup ?? false);

  /** Which dialog to show, or null once dismissed / when there's nothing to show. */
  readonly view = computed<'new-user' | 'post-install' | null>(() => {
    if (this.dismissed()) return null;
    const intent = this.info()?.intent;
    return intent === 'new-user' || intent === 'post-install' ? intent : null;
  });

  readonly trayNote = computed(() =>
    this.platform.os() === 'mac'
      ? `PCPanel keeps running in the background. There's no menu-bar icon yet, so reopen this page any time at ${location.origin}.`
      : 'PCPanel keeps running in your system tray — click its icon any time to reopen this page.');

  constructor() {
    this.onboarding.load();
  }

  setOpenOnStartup(on: boolean): void {
    const cur = this.settings.settings.value();
    if (!cur) return;
    this.settings.updateSettings({ ...cur, openBrowserOnStartup: on }).subscribe({
      next: () => this.settings.settings.reload(),
    });
  }

  dismiss(): void {
    this.dismissed.set(true);
    this.onboarding.ack();
  }
}
