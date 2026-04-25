import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
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
import { HomeFacade } from './home.facade';
import { ConnectionStatusComponent } from '../../components/connection-status/connection-status.component';
import { KeyValuePipe } from '@angular/common';
import { PcpanelProComponent } from '../../devices/pcpanel/pro/pcpanel-pro.component';
import { PcpanelMiniComponent } from '../../devices/pcpanel/mini/pcpanel-mini.component';
import { PcpanelRgbComponent } from '../../devices/pcpanel/rgb/pcpanel-rgb.component';
import { MatDialog } from '@angular/material/dialog';
import { SettingsComponent } from '../settings/settings.component';

@Component({
  selector: 'app-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterModule, FormsModule,
    MatSidenavModule, MatToolbarModule, MatListModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule,
    MatSliderModule, MatTooltipModule,
    ConnectionStatusComponent, KeyValuePipe, PcpanelProComponent, PcpanelMiniComponent, PcpanelRgbComponent,
  ],
  providers: [HomeFacade],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  protected readonly facade = inject(HomeFacade);
  private readonly dialog = inject(MatDialog);

  protected openSettings() {
    this.dialog.open(SettingsComponent)
  }
}
