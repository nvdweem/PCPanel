import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AudioDevice, ProcessDto } from '../../models/models';
import { AudioService } from '../../services/audio.service';
import { ProcessService } from '../../services/process.service';

@Component({
  selector: 'app-audio-picker',
  standalone: true,
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './audio-picker.component.html',
  styleUrl: './audio-picker.component.scss',
})
export class AudioPickerComponent implements OnInit {
  @Input() mode: 'process' | 'device' = 'process';
  @Input() deviceFilter: 'output' | 'input' | 'all' = 'all';
  @Input() value = '';
  @Input() placeholder?: string;
  @Output() valueChange = new EventEmitter<string>();

  processes: ProcessDto[] = [];
  devices: AudioDevice[] = [];

  constructor(private audioService: AudioService, private processService: ProcessService) {}

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
