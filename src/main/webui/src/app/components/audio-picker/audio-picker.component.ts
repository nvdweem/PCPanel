import { ChangeDetectionStrategy, Component, inject, input, model, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AudioDevice } from '../../models/models';
import { AudioService } from '../../services/audio.service';
import { ProcessService } from '../../services/process.service';
import { ProcessDto } from '../../models/generated/backend.types';

@Component({
  selector: 'app-audio-picker',
  imports: [FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './audio-picker.component.html',
  styleUrl: './audio-picker.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AudioPickerComponent implements OnInit {
  private audioService = inject(AudioService);
  private processService = inject(ProcessService);

  mode = input<'process' | 'device'>('process');
  deviceFilter = input<'output' | 'input' | 'all'>('all');
  value = model('');
  placeholder = input<string | undefined>(undefined);

  processes = signal<ProcessDto[]>([]);
  devices = signal<AudioDevice[]>([]);

  ngOnInit(): void {
    if (this.mode() === 'process') {
      this.processService.listProcesses().subscribe(p => this.processes.set(p));
    } else {
      const obs$ = this.deviceFilter() === 'output'
        ? this.audioService.listOutputDevices()
        : this.deviceFilter() === 'input'
          ? this.audioService.listInputDevices()
          : this.audioService.listDevices();
      obs$.subscribe(d => this.devices.set(d));
    }
  }

  selectProcess(p: ProcessDto): void {
    this.value.set(p.name);
  }
}
