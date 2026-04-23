import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { DeviceService } from '../../services/device.service';
import { CommandConfigComponent, CommandDialogData } from '../../components/command-config/command-config.component';
import { PcpanelProComponent } from '../../components/device-visual/pcpanel-pro.component';
import { PcpanelMiniComponent } from '../../components/device-visual/pcpanel-mini.component';
import { PcpanelRgbComponent } from '../../components/device-visual/pcpanel-rgb.component';
import { HomeFacade } from './home.facade';
import { ConnectionStatusComponent } from '../../components/connection-status/connection-status.component';
import { KeyValuePipe } from '@angular/common';
import { Commands } from '../../models/generated/backend.types';
import { DeviceClickEvent } from '../../components/device-visual/events';

@Component({
  selector: 'app-home',
  imports: [
    RouterModule, FormsModule,
    MatSidenavModule, MatToolbarModule, MatListModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatSliderModule, MatTooltipModule,
    PcpanelProComponent, PcpanelMiniComponent, PcpanelRgbComponent,
    ConnectionStatusComponent, KeyValuePipe,
  ],
  providers: [HomeFacade],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private deviceService = inject(DeviceService);
  private dialog = inject(MatDialog);
  protected readonly facade = inject(HomeFacade);

  onDialClick(index: number): void {
    this.facade.setActiveDial(index);
    const data: CommandDialogData = {
      kind: 'dial', index,
      currentCommands: this.facade.dialCommands().get(index) ?? null,
      profiles: this.facade.selectedDevice()?.profiles ?? [],
    };
    const ref = this.dialog.open(CommandConfigComponent, {data, width: '560px'});
    ref.afterClosed().subscribe((result: Commands | null | undefined) => {
      this.facade.setActiveDial(null);
      const dev = this.facade.selectedDevice();
      if (result && dev?.currentProfile) {
        const {serial, currentProfile} = dev;
        this.deviceService.setDialCommands(serial, currentProfile, index, result).subscribe(() => {
          this.facade.updateDialCommand(index, result);
        });
      }
    });
  }

  protected triggerEdit(event: DeviceClickEvent) {
    this.onDialClick(event.idx);
  }
}
