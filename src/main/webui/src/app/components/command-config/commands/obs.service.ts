import { Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';

@Injectable({providedIn: 'root'})
export class ObsService {
  // TODO: reset when save changes
  scenes = httpResource<string[]>(() => '/api/obs/scenes');
  sources = httpResource<string[]>(() => '/api/obs/sources');
}
