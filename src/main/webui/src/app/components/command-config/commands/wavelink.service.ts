import { computed, Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { WaveLinkResponseDto } from '../../../models/generated/backend.types';

@Injectable({providedIn: 'root'})
export class WaveLinkService {
  // TODO: reset when save changes
  devices = httpResource<WaveLinkResponseDto>(() => '/api/wavelink/devices');

  channels = computed(() => this.devices.value()?.channels ?? []);
  inputs = computed(() => this.devices.value()?.inputs ?? []);
  mixes = computed(() => this.devices.value()?.mixes ?? []);
  outputs = computed(() => this.devices.value()?.outputs ?? []);
}
