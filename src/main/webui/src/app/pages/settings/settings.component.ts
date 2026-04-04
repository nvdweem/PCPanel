import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
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
import { MqttSettings, SettingsDto, WaveLinkSettings } from '../../models/models';
import { SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    RouterModule, FormsModule,
    MatToolbarModule, MatTabsModule, MatCheckboxModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatSnackBarModule,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  settings: SettingsDto | null = null;
  mqtt: MqttSettings | null = null;
  waveLink: WaveLinkSettings | null = null;

  constructor(private settingsService: SettingsService, private snack: MatSnackBar) {}

  ngOnInit(): void {
    this.settingsService.getSettings().subscribe(s => this.settings = s);
    this.settingsService.getMqttSettings().subscribe(m => this.mqtt = m);
    this.settingsService.getWaveLinkSettings().subscribe(w => this.waveLink = w);
  }

  save(): void {
    if (!this.settings) return;
    const calls: Promise<void>[] = [];
    calls.push(this.settingsService.updateSettings(this.settings).toPromise() as Promise<void>);
    if (this.mqtt) calls.push(this.settingsService.updateMqttSettings(this.mqtt).toPromise() as Promise<void>);
    if (this.waveLink) calls.push(this.settingsService.updateWaveLinkSettings(this.waveLink).toPromise() as Promise<void>);
    Promise.all(calls).then(() => this.snack.open('Settings saved', 'OK', { duration: 2000 }));
  }
}
