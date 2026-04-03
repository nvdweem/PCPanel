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
import { MqttSettings, SettingsDto } from '../../models/models';
import { SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [
    RouterModule, FormsModule,
    MatToolbarModule, MatTabsModule, MatCheckboxModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {
  settings: SettingsDto | null = null;
  mqtt: MqttSettings | null = null;

  constructor(private settingsService: SettingsService) {}

  ngOnInit(): void {
    this.settingsService.getSettings().subscribe(s => this.settings = s);
    this.settingsService.getMqttSettings().subscribe(m => this.mqtt = m);
  }

  save(): void {
    if (!this.settings) return;
    this.settingsService.updateSettings(this.settings).subscribe(() => {
      if (this.mqtt) {
        this.settingsService.updateMqttSettings(this.mqtt).subscribe(() => alert('Settings saved!'));
      } else {
        alert('Settings saved!');
      }
    });
  }
}
