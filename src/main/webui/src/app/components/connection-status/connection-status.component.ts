import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { DeviceStateService } from '../../services/device-state.service';

type ConnectionState = 'connected' | 'reconnecting' | 'error' | 'connecting';

@Component({
  selector: 'app-connection-status',
  imports: [MatIconModule],
  templateUrl: './connection-status.component.html',
  styleUrl: './connection-status.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConnectionStatusComponent {
  private readonly deviceState = inject(DeviceStateService);

  protected readonly state = computed<ConnectionState>(() => {
    if (this.deviceState.reconnecting()) return 'reconnecting';
    if (this.deviceState.connected()) return 'connected';
    if (this.deviceState.lastError()) return 'error';
    return 'connecting';
  });

  protected readonly label = computed(() => {
    switch (this.state()) {
      case 'connected':
        return 'Connected';
      case 'reconnecting':
        return 'Reconnecting...';
      case 'error':
        return 'Connection error';
      default:
        return 'Connecting...';
    }
  });

  protected readonly icon = computed(() => {
    switch (this.state()) {
      case 'connected':
        return 'wifi';
      case 'reconnecting':
        return 'sync';
      case 'error':
        return 'wifi_off';
      default:
        return 'wifi_tethering';
    }
  });

  protected readonly errorText = computed(() => this.deviceState.lastError());
}
