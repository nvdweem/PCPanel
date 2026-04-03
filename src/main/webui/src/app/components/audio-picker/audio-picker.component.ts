import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AudioDevice, ProcessDto } from '../../models/models';
import { AudioService } from '../../services/audio.service';
import { ProcessService } from '../../services/process.service';

/**
 * Reusable picker for selecting a process name or audio device ID.
 * Use [mode]="'process'" to pick a process executable name,
 * or [mode]="'device'" to pick an audio device ID (optionally filtered to output/input).
 */
@Component({
  selector: 'app-audio-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="audio-picker">
      <div *ngIf="mode === 'process'" class="picker-section">
        <input
          type="text"
          class="picker-input"
          [placeholder]="placeholder || 'Process name (e.g. chrome.exe)'"
          [(ngModel)]="value"
          (ngModelChange)="valueChange.emit($event)"
          list="process-list" />
        <datalist id="process-list">
          <option *ngFor="let p of processes" [value]="p.name">{{ p.name }}</option>
        </datalist>
        <div class="process-thumbnails" *ngIf="processes.length">
          <button *ngFor="let p of processes"
                  class="process-thumb"
                  [class.selected]="value === p.name"
                  (click)="selectProcess(p)"
                  type="button"
                  [title]="p.path">
            <img *ngIf="p.icon" [src]="p.icon" width="20" height="20" alt="" />
            <span class="process-name">{{ p.name }}</span>
          </button>
        </div>
      </div>

      <div *ngIf="mode === 'device'" class="picker-section">
        <select class="picker-select" [(ngModel)]="value" (ngModelChange)="valueChange.emit($event)">
          <option value="">Default Device</option>
          <option *ngFor="let d of devices" [value]="d.id">{{ d.name }}</option>
        </select>
      </div>
    </div>
  `,
  styles: [`
    .audio-picker { display: flex; flex-direction: column; gap: 6px; }
    .picker-input, .picker-select { width: 100%; padding: 6px 8px; border: 1px solid #444; border-radius: 4px; background: #2a2a2a; color: #eee; font-size: 13px; box-sizing: border-box; }
    .process-thumbnails { display: flex; flex-wrap: wrap; gap: 4px; max-height: 120px; overflow-y: auto; border: 1px solid #333; border-radius: 4px; padding: 4px; background: #1e1e1e; }
    .process-thumb { display: flex; align-items: center; gap: 4px; padding: 3px 6px; border: 1px solid #333; border-radius: 3px; background: #2a2a2a; color: #ccc; cursor: pointer; font-size: 12px; }
    .process-thumb:hover { background: #3a3a3a; }
    .process-thumb.selected { border-color: #4a90d9; background: #1a3a5a; color: #fff; }
    .process-name { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  `]
})
export class AudioPickerComponent implements OnInit {
  @Input() mode: 'process' | 'device' = 'process';
  /** 'output' | 'input' | 'all' — only used for device mode */
  @Input() deviceFilter: 'output' | 'input' | 'all' = 'all';
  @Input() value = '';
  @Input() placeholder?: string;
  @Output() valueChange = new EventEmitter<string>();

  processes: ProcessDto[] = [];
  devices: AudioDevice[] = [];

  constructor(
    private audioService: AudioService,
    private processService: ProcessService
  ) {}

  ngOnInit(): void {
    if (this.mode === 'process') {
      this.processService.listProcesses().subscribe(p => this.processes = p);
    } else {
      const obs$ = this.deviceFilter === 'output'
        ? this.audioService.listOutputDevices()
        : this.deviceFilter === 'input'
          ? this.audioService.listInputDevices()
          : this.audioService.listDevices();
      obs$.subscribe(d => this.devices = d);
    }
  }

  selectProcess(p: ProcessDto): void {
    this.value = p.name;
    this.valueChange.emit(p.name);
  }
}
