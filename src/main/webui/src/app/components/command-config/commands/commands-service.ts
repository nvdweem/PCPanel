import { Injectable } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { CommandType } from '../../../models/generated/backend.types';

@Injectable({
  providedIn: 'root',
})
export class CommandsService {
  // TODO: reset when save changes
  commands = httpResource<CommandType[]>(() => '/api/commands/available');
}
