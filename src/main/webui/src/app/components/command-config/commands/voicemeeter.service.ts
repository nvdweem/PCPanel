import { Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';

export interface VoiceMeeterParams {
  name: string;
  params: string[];
}

@Injectable({providedIn: 'root'})
export class VoiceMeeterService {
  // TODO: reset when save changes
  basicParams = httpResource<VoiceMeeterParams[]>(() => '/api/voicemeeter/basic');
  advancedParams = httpResource<VoiceMeeterParams[]>(() => '/api/voicemeeter/advanced');
}
