import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Commands, ControlAssignmentsUpdateDto, DeviceDto, KnobSetting, LightingConfig, ProfileDto } from '../models/generated/backend.types';

@Injectable({providedIn: 'root'})
export class DeviceService {
  private readonly base = '/api/devices';

  constructor(private http: HttpClient) {
  }

  listDevices(): Observable<DeviceDto[]> {
    return this.http.get<DeviceDto[]>(this.base);
  }

  getDevice(serial: string): Observable<DeviceDto> {
    return this.http.get<DeviceDto>(`${this.base}/${serial}`);
  }

  renameDevice(serial: string, name: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/name`, name, {headers: {'Content-Type': 'text/plain'}});
  }

  // Profiles
  listProfiles(serial: string): Observable<ProfileDto[]> {
    return this.http.get<ProfileDto[]>(`${this.base}/${serial}/profiles`);
  }

  createProfile(serial: string, name: string): Observable<ProfileDto> {
    return this.http.post<ProfileDto>(`${this.base}/${serial}/profiles`, name, {headers: {'Content-Type': 'text/plain'}});
  }

  deleteProfile(serial: string, name: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${serial}/profiles/${name}`);
  }

  switchProfile(serial: string, name: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/profiles/current`, name, {headers: {'Content-Type': 'text/plain'}});
  }

  // Button/dial commands
  // @ts-ignore
  getButtonCommands(serial: string, profile: string, index: number): Observable<Commands> {
    // @ts-ignore
    return this.http.get<Commands>(`${this.base}/${serial}/profiles/${profile}/buttons/${index}`);
  }

  setButtonCommands(serial: string, profile: string, index: number, commands: Commands): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/profiles/${profile}/buttons/${index}`, commands);
  }

  getDblButtonCommands(serial: string, profile: string, index: number): Observable<Commands> {
    return this.http.get<Commands>(`${this.base}/${serial}/profiles/${profile}/dblbuttons/${index}`);
  }

  setDblButtonCommands(serial: string, profile: string, index: number, commands: Commands): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/profiles/${profile}/dblbuttons/${index}`, commands);
  }

  getDialCommands(serial: string, profile: string, index: number): Observable<Commands> {
    return this.http.get<Commands>(`${this.base}/${serial}/profiles/${profile}/dials/${index}`);
  }

  setDialCommands(serial: string, profile: string, index: number, commands: Commands): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/profiles/${profile}/dials/${index}`, commands);
  }

  getKnobSettings(serial: string, profile: string, index: number): Observable<KnobSetting> {
    return this.http.get<KnobSetting>(`${this.base}/${serial}/profiles/${profile}/knobsettings/${index}`);
  }

  setKnobSettings(serial: string, profile: string, index: number, settings: KnobSetting): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/profiles/${profile}/knobsettings/${index}`, settings);
  }

  setControlAssignments(serial: string, profile: string, index: number, update: ControlAssignmentsUpdateDto): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/profiles/${profile}/controls/${index}`, update);
  }

  // Lighting
  getLighting(serial: string): Observable<LightingConfig> {
    return this.http.get<LightingConfig>(`${this.base}/${serial}/lighting`);
  }

  setLighting(serial: string, config: LightingConfig): Observable<void> {
    return this.http.put<void>(`${this.base}/${serial}/lighting`, config);
  }
}
