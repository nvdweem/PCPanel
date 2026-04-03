import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MqttSettings, SettingsDto } from '../../models/models';
import { SettingsService } from '../../services/settings.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [RouterModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnInit {
  settings: SettingsDto | null = null;
  mqtt: MqttSettings | null = null;
  activeTab = 'General';
  tabs = ['General', 'OBS', 'VoiceMeeter', 'Overlay', 'MQTT'];

  constructor(private settingsService: SettingsService) {}

  ngOnInit(): void {
    this.settingsService.getSettings().subscribe(s => this.settings = s);
    this.settingsService.getMqttSettings().subscribe(m => this.mqtt = m);
  }

  save(): void {
    if (!this.settings) return;
    this.settingsService.updateSettings(this.settings).subscribe(() => {
      if (this.mqtt) {
        this.settingsService.updateMqttSettings(this.mqtt).subscribe(() => {
          alert('Settings saved!');
        });
      } else {
        alert('Settings saved!');
      }
    });
  }
}
