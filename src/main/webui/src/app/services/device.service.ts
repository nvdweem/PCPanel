import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AddDeejDeviceDto, Commands, ControlAssignmentsUpdateDto, DeviceDto, KnobSetting, LightingConfig, ProfileDto, ProfileSettingsDto, SerialPortDto } from '../models/generated/backend.types';

/**
 * Percent-encodes one URL path segment. Profile names are user-chosen free text, and the browser
 * resolves the request URL before sending it: a `#` starts the fragment (which never reaches the
 * server), a `?` starts the query, and a `/` invents a path segment — so an unencoded name silently
 * addresses a different endpoint than the one intended, and the request 404s (issue #150). Every
 * interpolated segment goes through this, including the serial, so the rule needs no case-by-case
 * judgement about which values "can" contain such a character.
 */
function seg(value: string | number): string {
  return encodeURIComponent(String(value));
}

@Injectable({providedIn: 'root'})
export class DeviceService {
  private readonly base = '/api/devices';
  private readonly serialBase = '/api/serial';

  constructor(private http: HttpClient) {
  }

  // Serial / Deej (manual-add provider)
  listSerialPorts(): Observable<SerialPortDto[]> {
    return this.http.get<SerialPortDto[]>(`${this.serialBase}/ports`);
  }

  addDeej(req: AddDeejDeviceDto): Observable<string> {
    return this.http.post(`${this.serialBase}/deej`, req, {responseType: 'text'});
  }

  removeDeej(id: string): Observable<void> {
    return this.http.delete<void>(`${this.serialBase}/deej/${seg(id)}`);
  }

  listDevices(): Observable<DeviceDto[]> {
    return this.http.get<DeviceDto[]>(this.base);
  }

  getDevice(serial: string): Observable<DeviceDto> {
    return this.http.get<DeviceDto>(`${this.base}/${seg(serial)}`);
  }

  renameDevice(serial: string, name: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/name`, name, {headers: {'Content-Type': 'text/plain'}});
  }

  // Profiles
  listProfiles(serial: string): Observable<ProfileDto[]> {
    return this.http.get<ProfileDto[]>(`${this.base}/${seg(serial)}/profiles`);
  }

  createProfile(serial: string, name: string): Observable<ProfileDto> {
    return this.http.post<ProfileDto>(`${this.base}/${seg(serial)}/profiles`, name, {headers: {'Content-Type': 'text/plain'}});
  }

  deleteProfile(serial: string, name: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${seg(serial)}/profiles/${seg(name)}`);
  }

  renameProfile(serial: string, oldName: string, newName: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/${seg(oldName)}/rename`, newName, {headers: {'Content-Type': 'text/plain'}});
  }

  switchProfile(serial: string, name: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/current`, name, {headers: {'Content-Type': 'text/plain'}});
  }

  reorderProfiles(serial: string, order: string[]): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/order`, order);
  }

  // Per-profile activation settings (auto-switch on app focus)
  getProfileSettings(serial: string, name: string): Observable<ProfileSettingsDto> {
    return this.http.get<ProfileSettingsDto>(`${this.base}/${seg(serial)}/profiles/${seg(name)}/settings`);
  }

  setProfileSettings(serial: string, name: string, settings: ProfileSettingsDto): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/${seg(name)}/settings`, settings);
  }

  // Button/dial commands
  getButtonCommands(serial: string, profile: string, index: number): Observable<Commands> {
    return this.http.get<Commands>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/buttons/${seg(index)}`);
  }

  setButtonCommands(serial: string, profile: string, index: number, commands: Commands): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/buttons/${seg(index)}`, commands);
  }

  getDblButtonCommands(serial: string, profile: string, index: number): Observable<Commands> {
    return this.http.get<Commands>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/dblbuttons/${seg(index)}`);
  }

  setDblButtonCommands(serial: string, profile: string, index: number, commands: Commands): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/dblbuttons/${seg(index)}`, commands);
  }

  getDialCommands(serial: string, profile: string, index: number): Observable<Commands> {
    return this.http.get<Commands>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/dials/${seg(index)}`);
  }

  setDialCommands(serial: string, profile: string, index: number, commands: Commands): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/dials/${seg(index)}`, commands);
  }

  getKnobSettings(serial: string, profile: string, index: number): Observable<KnobSetting> {
    return this.http.get<KnobSetting>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/knobsettings/${seg(index)}`);
  }

  setKnobSettings(serial: string, profile: string, index: number, settings: KnobSetting): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/knobsettings/${seg(index)}`, settings);
  }

  setControlAssignments(serial: string, profile: string, index: number, update: ControlAssignmentsUpdateDto): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/profiles/${seg(profile)}/controls/${seg(index)}`, update);
  }

  // Lighting
  getLighting(serial: string): Observable<LightingConfig> {
    return this.http.get<LightingConfig>(`${this.base}/${seg(serial)}/lighting`);
  }

  setLighting(serial: string, config: LightingConfig): Observable<void> {
    return this.http.put<void>(`${this.base}/${seg(serial)}/lighting`, config);
  }
}
