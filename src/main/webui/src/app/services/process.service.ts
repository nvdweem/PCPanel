import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProcessDto } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ProcessService {
  constructor(private http: HttpClient) {}

  listProcesses(): Observable<ProcessDto[]> {
    return this.http.get<ProcessDto[]>('/api/processes');
  }
}
