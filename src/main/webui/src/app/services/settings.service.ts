import { inject, Injectable } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SettingsDto } from '../models/generated/backend.types';

@Injectable()
export class SettingsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/settings';
  settings = httpResource<SettingsDto>(() => '/api/settings');

  updateSettings(settings: SettingsDto): Observable<void> {
    return this.http.put<void>(this.base, settings);
  }
}
