import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { CommandConfigComponent, CommandDialogData } from './command-config/command-config.component';
import { filter } from 'rxjs';
import { DeviceService } from '../../services/device.service';
import { DeviceStateService } from '../../services/device-state.service';

interface OpenCommandDialogParams {
  serial: string;
  controlIdx: number;
  data: CommandDialogData;
}

@Injectable({
  providedIn: 'root',
})
export class PcpanelCommandService {
  private readonly dialog = inject(MatDialog);
  private readonly deviceService = inject(DeviceService);
  private readonly deviceState = inject(DeviceStateService);

  openCommandDialog({serial, controlIdx, data}: OpenCommandDialogParams) {
    const ref = this.dialog.open(CommandConfigComponent, {data, width: '560px'});
    ref.afterClosed().pipe(filter((x): x is CommandDialogData => !!x)).subscribe((result) => {
      const currentProfile = this.deviceState.devices()[serial]?.currentProfile;
      if (!currentProfile) {
        return;
      }

      this.deviceService.setControlAssignments(serial, currentProfile, controlIdx, {
        analog: result.analog,
        button: result.button,
        dblButton: result.dblButton,
        knobSetting: result.knobSetting,
      }).subscribe({
        error: err => console.error('Failed to save command settings', err),
      });
    });
  }
}
