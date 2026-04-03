import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MqttSettings, SettingsDto } from '../models/models';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly base = '/api/settings';

  constructor(private http: HttpClient) {}

  getSettings(): Observable<SettingsDto> {
    return this.http.get<SettingsDto>(this.base);
  }

  updateSettings(settings: SettingsDto): Observable<void> {
    return this.http.put<void>(this.base, settings);
  }

  getMqttSettings(): Observable<MqttSettings> {
    return this.http.get<MqttSettings>(`${this.base}/mqtt`);
  }

  updateMqttSettings(settings: MqttSettings): Observable<void> {
    return this.http.put<void>(`${this.base}/mqtt`, settings);
  }
}
