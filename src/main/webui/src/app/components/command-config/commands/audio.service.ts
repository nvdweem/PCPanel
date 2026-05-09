import { computed, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { AudioDevice, AudioSession } from '../../../models/models';
import { ProcessDto } from '../../../models/generated/backend.types';

@Injectable({providedIn: 'root'})
export class AudioService {
  // TODO: reset when save changes
  devices = httpResource<AudioDevice[]>(() => '/api/audio/devices');
  sessions = httpResource<AudioSession[]>(() => '/api/audio/sessions');
  processes = httpResource<ProcessDto[]>(() => '/api/processes');

  uniqueProcesses = computed(() => [...new Set(this.processes.value() ?? [])].sort())
}
