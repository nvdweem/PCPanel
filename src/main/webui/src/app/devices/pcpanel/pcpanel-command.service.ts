import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { CommandConfigComponent, CommandDialogData } from './command-config/command-config.component';
import { filter } from 'rxjs';

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

  openCommandDialog({serial, controlIdx, data}: OpenCommandDialogParams) {
    const ref = this.dialog.open(CommandConfigComponent, {data, width: '560px'});
    ref.afterClosed().pipe(filter(x => x)).subscribe((result: CommandDialogData) => {

      console.log(serial, controlIdx, result);

    });
  }
}
