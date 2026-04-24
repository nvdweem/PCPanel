import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterModule } from '@angular/router';
import { form, FormField, max, min } from '@angular/forms/signals';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { SettingsService } from '../../services/settings.service';
import { forkJoin } from 'rxjs';
import { ConnectionStatusComponent } from '../../components/connection-status/connection-status.component';
import { MqttSettings, SettingsDto, WaveLinkSettings } from '../../models/generated/backend.types';

const DEFAULT_SETTINGS: SettingsDto = {
  mainUIIcons: true,
  startupVersionCheck: true,
  forceVolume: false,
  dblClickInterval: 250,
  preventClickWhenDblClick: true,
  preventSliderTwitchDelay: undefined,
  sliderRollingAverage: undefined,
  sendOnlyIfDelta: undefined,
  workaroundsOnlySliders: false,
  obsEnabled: false,
  obsAddress: 'localhost',
  obsPort: '4455',
  obsPassword: '',
  voicemeeterEnabled: false,
  voicemeeterPath: '',
  oscListenPort: 0,
  oscConnections: [],
  overlayEnabled: false,
  overlayUseLog: false,
  overlayShowNumber: true,
  overlayBackgroundColor: '#000000',
  overlayTextColor: '#ffffff',
  overlayBarColor: '#00ff00',
  overlayBarBackgroundColor: '#000000',
  overlayWindowCornerRounding: undefined,
  overlayBarHeight: undefined,
  overlayBarCornerRounding: undefined,
  overlayPosition: 'bottomRight',
  overlayPadding: undefined,
};

const DEFAULT_MQTT: MqttSettings = {
  enabled: false,
  host: 'localhost',
  port: 1883,
  secure: false,
  username: '',
  password: '',
  baseTopic: 'pcpanel',
  homeAssistant: {
    availability: false,
    baseTopic: '',
    enableDiscovery: false,
  },
};

const DEFAULT_WAVELINK: WaveLinkSettings = {
  enabled: false,
};

@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterModule, FormField,
    MatToolbarModule, MatTabsModule, MatCheckboxModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatSnackBarModule,
    ConnectionStatusComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private settingsService = inject(SettingsService);
  private snack = inject(MatSnackBar);
  private destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly settingsModel = signal<SettingsDto>({...DEFAULT_SETTINGS});
  protected readonly mqttModel = signal<MqttSettings>({...DEFAULT_MQTT});
  protected readonly waveLinkModel = signal<WaveLinkSettings>({...DEFAULT_WAVELINK});

  protected readonly settingsForm = form(this.settingsModel, (settings) => {
    min(settings.dblClickInterval, 1);
    max(settings.dblClickInterval, 2000);
    min(settings.oscListenPort, 1);
    max(settings.oscListenPort, 65535);
  });
  protected readonly mqttForm = form(this.mqttModel, (mqtt) => {
    min(mqtt.port, 1);
    max(mqtt.port, 65535);
  });
  protected readonly waveLinkForm = form(this.waveLinkModel);

  ngOnInit(): void {
    forkJoin({
      settings: this.settingsService.getSettings(),
      mqtt: this.settingsService.getMqttSettings(),
      waveLink: this.settingsService.getWaveLinkSettings(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({settings, mqtt, waveLink}) => {
          this.settingsModel.set(settings);
          this.mqttModel.set(mqtt);
          this.waveLinkModel.set(waveLink);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.snack.open('Failed to load settings', 'OK', {duration: 3000});
        },
      });
  }

  save(): void {
    if (this.saving()) {
      return;
    }

    this.saving.set(true);
    forkJoin([
      this.settingsService.updateSettings(this.settingsModel()),
      this.settingsService.updateMqttSettings(this.mqttModel()),
      this.settingsService.updateWaveLinkSettings(this.waveLinkModel()),
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.snack.open('Settings saved', 'OK', {duration: 2000}),
        error: () => this.snack.open('Failed to save settings', 'OK', {duration: 3000}),
        complete: () => this.saving.set(false),
      });
  }
}
