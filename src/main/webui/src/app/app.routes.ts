import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home.component';
import { DeviceComponent } from './pages/device/device.component';
import { LightingComponent } from './pages/lighting/lighting.component';
import { SettingsComponent } from './pages/settings/settings.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'device/:serial', component: DeviceComponent },
  { path: 'lighting/:serial', component: LightingComponent },
  { path: 'settings', component: SettingsComponent },
  { path: '**', redirectTo: '' }
];
