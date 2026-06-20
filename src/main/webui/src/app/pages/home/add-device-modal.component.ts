import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { DeviceService } from '../../services/device.service';
import {
  IconComponent, ModalComponent, SegmentedComponent, SegmentOption,
  SelectComponent, SelectOption, SpinnerComponent, ToastService,
} from '../../ui';
import { SerialPortDto } from '../../models/generated/backend.types';

const BAUD_OPTIONS: SelectOption<number>[] = [9600, 19200, 38400, 57600, 115200]
  .map(b => ({ value: b, label: String(b) }));

const NOISE_OPTIONS: SegmentOption<string>[] = [
  { value: 'low', label: 'Low' },
  { value: 'default', label: 'Default' },
  { value: 'high', label: 'High' },
];

/**
 * Modal for adding a Deej (serial mixer) device — the only MANUAL-add provider today.
 * The user picks a serial port, baud rate, noise-reduction level and an optional name,
 * then we POST it; the device shows up in the list via the WebSocket connect event.
 */
@Component({
  selector: 'app-add-device-modal',
  standalone: true,
  imports: [ModalComponent, SelectComponent, SegmentedComponent, SpinnerComponent, IconComponent],
  template: `
    <pc-modal [open]="open()" heading="Add a device" [width]="380" (dismiss)="close()">
      <div class="add-body">
        <p class="lead">Add a <strong>Deej</strong> serial mixer. Connect it over USB, then pick its
          serial port below. PCPanel devices are detected automatically and don't need this.</p>

        <label class="field">
          <span class="field-label">Serial port</span>
          @if (loadingPorts()) {
            <span class="loading-row"><pc-spinner [size]="14" [thickness]="2"></pc-spinner> Scanning ports…</span>
          } @else if (!ports().length) {
            <div class="no-ports">
              <span>No serial ports found — plug your device in.</span>
              <button type="button" class="pc-btn ghost" (click)="loadPorts()">
                <pc-icon name="refresh" [size]="13"></pc-icon> Refresh
              </button>
            </div>
          } @else {
            <div class="port-row">
              <pc-select class="grow" [block]="true" [options]="portOptions()" [(value)]="port"
                         placeholder="Select a port…" [panelWidth]="320"></pc-select>
              <button type="button" class="pc-btn ghost icon" title="Refresh ports" (click)="loadPorts()">
                <pc-icon name="refresh" [size]="14"></pc-icon>
              </button>
            </div>
          }
        </label>

        <label class="field">
          <span class="field-label">Baud rate</span>
          <pc-select [block]="true" [options]="baudOptions" [(value)]="baud"></pc-select>
        </label>

        <label class="field">
          <span class="field-label">Noise reduction</span>
          <pc-segmented [options]="noiseOptions" [(value)]="noise"></pc-segmented>
        </label>

        <label class="field">
          <span class="field-label">Name <span class="opt">(optional)</span></span>
          <input class="pc-input" placeholder="e.g. Deej Mixer" [value]="name()"
                 (input)="name.set($any($event.target).value)">
        </label>

        <div class="actions">
          <button class="pc-btn ghost" (click)="close()">Cancel</button>
          <button class="pc-btn primary" [disabled]="!port() || saving()" (click)="confirm()">
            @if (saving()) { <pc-spinner [size]="13" [thickness]="2"></pc-spinner> } Add device
          </button>
        </div>
      </div>
    </pc-modal>
  `,
  styles: [`
    .add-body { width: 332px; display: flex; flex-direction: column; gap: 14px; }
    .lead { font-size: 12.5px; color: var(--text-2); line-height: 1.5; margin: 0; }
    .field { display: flex; flex-direction: column; gap: 6px; }
    .field-label { font-size: 11.5px; color: var(--text-2); }
    .field-label .opt { color: var(--text-3); }
    .loading-row { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--text-2); padding: 4px 0; }
    .no-ports { display: flex; flex-direction: column; gap: 8px; font-size: 12.5px; color: var(--text-3); }
    .no-ports .pc-btn { align-self: flex-start; }
    .port-row { display: flex; align-items: center; gap: 8px; }
    .port-row .grow { flex: 1; min-width: 0; }
    .actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px; }
    .pc-btn pc-spinner { margin-right: 4px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddDeviceModalComponent {
  private readonly deviceService = inject(DeviceService);
  private readonly toast = inject(ToastService);

  readonly open = input<boolean>(false);
  readonly dismiss = output<void>();

  readonly baudOptions = BAUD_OPTIONS;
  readonly noiseOptions = NOISE_OPTIONS;

  readonly ports = signal<SerialPortDto[]>([]);
  readonly loadingPorts = signal(false);
  readonly saving = signal(false);

  readonly port = signal<string | undefined>(undefined);
  readonly baud = signal<number | undefined>(9600);
  readonly noise = signal<string | undefined>('default');
  readonly name = signal('');

  readonly portOptions = computed<SelectOption[]>(() =>
    this.ports().map(p => ({ value: p.port, label: p.port, hint: p.description })));

  constructor() {
    // Refresh the port list each time the modal is opened.
    let wasOpen = false;
    effect(() => {
      const isOpen = this.open();
      if (isOpen && !wasOpen) this.loadPorts();
      wasOpen = isOpen;
    });
  }

  /** Load ports lazily when the modal is opened (called by the parent). */
  loadPorts(): void {
    this.loadingPorts.set(true);
    this.deviceService.listSerialPorts().subscribe({
      next: ports => {
        this.ports.set(ports);
        if (ports.length && !ports.some(p => p.port === this.port())) this.port.set(ports[0].port);
        this.loadingPorts.set(false);
      },
      error: () => {
        this.ports.set([]);
        this.loadingPorts.set(false);
        this.toast.show('Could not list serial ports', { kind: 'error' });
      },
    });
  }

  confirm(): void {
    const port = this.port();
    if (!port || this.saving()) return;
    this.saving.set(true);
    const name = this.name().trim();
    this.deviceService.addDeej({
      port,
      baud: this.baud(),
      noiseReduction: this.noise(),
      name: name || undefined,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.show('Device added', { kind: 'success' });
        this.close();
      },
      error: () => {
        this.saving.set(false);
        this.toast.show('Could not add device', { kind: 'error' });
      },
    });
  }

  close(): void {
    this.dismiss.emit();
  }
}
