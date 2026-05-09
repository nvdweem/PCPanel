import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AudioDevice, AudioSession } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AudioService {
  private readonly base = '/api/audio';

  constructor(private http: HttpClient) {}

  listDevices(): Observable<AudioDevice[]> {
    return this.http.get<AudioDevice[]>(`${this.base}/devices`);
  }

  listOutputDevices(): Observable<AudioDevice[]> {
    return this.http.get<AudioDevice[]>(`${this.base}/devices/output`);
  }

  listInputDevices(): Observable<AudioDevice[]> {
    return this.http.get<AudioDevice[]>(`${this.base}/devices/input`);
  }

  listSessions(): Observable<AudioSession[]> {
    return this.http.get<AudioSession[]>(`${this.base}/sessions`);
  }

  listApplications(): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/applications`);
  }
}
