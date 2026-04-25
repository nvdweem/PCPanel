import { Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';

export interface WaveLinkChannel {
  id: string;
  name: string;
}

export interface WaveLinkEffect {
  id: string;
  name: string;
}

@Injectable({providedIn: 'root'})
export class WaveLinkService {
  // TODO: reset when save changes
  channels = httpResource<WaveLinkChannel[]>(() => '/api/wavelink/channels');
  effects = httpResource<WaveLinkEffect[]>(() => '/api/wavelink/effects');
}
